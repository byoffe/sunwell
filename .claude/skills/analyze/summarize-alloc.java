import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reads allocation events from a JFR file and prints a ranked allocation
 * hotspot table, weighted by bytes allocated.
 *
 * Accepts three event types:
 *   jdk.ObjectAllocationSample        (JFR, field: weight)
 *   jdk.ObjectAllocationInNewTLAB     (async-profiler, field: allocationSize)
 *   jdk.ObjectAllocationOutsideTLAB   (async-profiler, field: allocationSize)
 *
 * Usage: java summarize-alloc.java <jfr-file> [--thread <pattern>] [--package <pkg>]
 *
 *   --thread  <pattern>  keep only events whose thread name contains <pattern>
 *   --package <pkg>      find first stack frame whose class starts with <pkg>;
 *                        otherwise the first non-JDK frame is used
 */
class SummarizeAlloc {

    // Frames from these prefixes are skipped when looking for the application frame.
    // This is a best-effort heuristic; not exhaustive for all JVM vendors.
    private static final List<String> JDK_PREFIXES = List.of(
        "java/", "jdk/", "sun/", "com/sun/", "com/oracle/", "org/openjdk/"
    );

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java summarize-alloc.java <jfr-file> [--thread <pattern>] [--package <pkg>]");
            System.exit(1);
        }

        Path file = Path.of(args[0]);
        if (!Files.exists(file)) {
            System.err.println("Error: file not found: " + file);
            System.exit(1);
        }
        if (!Files.isReadable(file)) {
            System.err.println("Error: file not readable: " + file);
            System.exit(1);
        }

        String threadPattern = argValue(args, "--thread");
        String rawPkg        = argValue(args, "--package");
        String packagePrefix = rawPkg != null ? rawPkg.replace('.', '/') : null;

        Map<String, Long> weights = new LinkedHashMap<>();
        long totalWeight = 0;
        long filtered    = 0;
        long noMatch     = 0;

        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                String eventName = e.getEventType().getName();
                // JFR native: jdk.ObjectAllocationSample (weight field)
                // async-profiler JFR output: jdk.ObjectAllocationInNewTLAB and
                //   jdk.ObjectAllocationOutsideTLAB (allocationSize field)
                long weight;
                switch (eventName) {
                    case "jdk.ObjectAllocationSample":
                        weight = e.getLong("weight");
                        break;
                    case "jdk.ObjectAllocationInNewTLAB":
                    case "jdk.ObjectAllocationOutsideTLAB":
                        weight = e.getLong("allocationSize");
                        break;
                    default:
                        continue;
                }

                // Thread filter
                RecordedThread thread = e.getThread("eventThread");
                if (threadPattern != null) {
                    String name = thread != null ? thread.getJavaName() : null;
                    if (name == null || !name.contains(threadPattern)) {
                        filtered++;
                        continue;
                    }
                }

                RecordedStackTrace stack = e.getStackTrace();
                if (stack == null || stack.getFrames().isEmpty()) continue;
                totalWeight += weight;

                List<RecordedFrame> frames = stack.getFrames();
                RecordedFrame appFrame = null;

                if (packagePrefix != null) {
                    for (RecordedFrame f : frames) {
                        if (internalName(f).startsWith(packagePrefix)) { appFrame = f; break; }
                    }
                } else {
                    // Walk down to first non-JDK frame
                    for (RecordedFrame f : frames) {
                        String internal = internalName(f);
                        boolean isJdk = JDK_PREFIXES.stream().anyMatch(internal::startsWith);
                        if (!isJdk) { appFrame = f; break; }
                    }
                }

                if (appFrame == null) {
                    // All frames are JDK internals or no package match — use top frame
                    appFrame = frames.get(0);
                    noMatch++;
                }

                weights.merge(frameKey(appFrame), weight, Long::sum);
            }
        }

        System.out.printf("Allocation Hotspots  [%s]%n", file.getFileName());
        System.out.printf("Total allocated (sampled): %s%n", formatBytes(totalWeight));
        if (threadPattern != null) System.out.printf("Thread filter:  %s  (%d events excluded)%n", threadPattern, filtered);
        if (packagePrefix  != null) System.out.printf("Package filter: %s%n", rawPkg);
        if (noMatch > 0)            System.out.printf("Note: %d events fell back to top-of-stack (no app frame found)%n", noMatch);
        System.out.println();

        if (weights.isEmpty()) { System.out.println("No samples matched."); return; }

        List<Map.Entry<String, Long>> sorted = weights.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toList());

        int limit = Math.min(20, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            // Guard against totalWeight=0 (all-zero weight events) to avoid Infinity output
            double pct = totalWeight > 0 ? 100.0 * entry.getValue() / totalWeight : 0.0;
            System.out.printf("  %5.1f%%  %9s  %s%n", pct, formatBytes(entry.getValue()), entry.getKey());
        }
        if (sorted.size() > limit) {
            System.out.printf("  ... and %d more site(s)%n", sorted.size() - limit);
        }
    }

    static String internalName(RecordedFrame f) {
        RecordedClass type = f.getMethod().getType();
        if (type == null) return "";
        String name = type.getName();
        return name != null ? name.replace('.', '/') : "";
    }

    static String frameKey(RecordedFrame f) {
        RecordedClass type = f.getMethod().getType();
        String cls  = type != null && type.getName() != null
            ? type.getName().replace('/', '.') : "Unknown";
        String meth = f.getMethod().getName();
        if (meth == null) meth = "unknown";
        int line = f.getLineNumber();
        return cls + "." + meth + (line > 0 ? ":" + line : "");
    }

    static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format("%.1f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L)     return String.format("%.1f MB", bytes / 1_000_000.0);
        if (bytes >= 1_000L)         return String.format("%.1f kB", bytes / 1_000.0);
        return bytes + " B";
    }

    static String argValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
