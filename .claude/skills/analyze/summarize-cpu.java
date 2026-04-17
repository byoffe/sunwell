import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reads jdk.ExecutionSample events from a JFR file and prints a ranked
 * CPU hotspot table.
 *
 * Usage: java summarize-cpu.java <jfr-file> [--thread <pattern>] [--package <pkg>]
 *
 *   --thread  <pattern>  keep only events whose thread name contains <pattern>
 *   --package <pkg>      find first stack frame whose class starts with <pkg>
 *                        instead of using the raw top-of-stack frame
 */
class SummarizeCpu {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java summarize-cpu.java <jfr-file> [--thread <pattern>] [--package <pkg>]");
            System.exit(1);
        }

        Path file = Path.of(args[0]);
        String threadPattern = argValue(args, "--thread");
        String rawPkg        = argValue(args, "--package");
        String packagePrefix = rawPkg != null ? rawPkg.replace('.', '/') : null;

        Map<String, Long> counts = new LinkedHashMap<>();
        long total       = 0;
        long filtered    = 0;
        long noMatch     = 0;

        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                if (!"jdk.ExecutionSample".equals(e.getEventType().getName())) continue;

                // Thread filter
                RecordedThread thread = e.getThread("sampledThread");
                if (threadPattern != null) {
                    String name = thread != null ? thread.getJavaName() : null;
                    if (name == null || !name.contains(threadPattern)) {
                        filtered++;
                        continue;
                    }
                }

                RecordedStackTrace stack = e.getStackTrace();
                if (stack == null || stack.getFrames().isEmpty()) continue;

                List<RecordedFrame> frames = stack.getFrames();
                RecordedFrame top = null;

                if (packagePrefix != null) {
                    for (RecordedFrame f : frames) {
                        if (internalName(f).startsWith(packagePrefix)) { top = f; break; }
                    }
                    if (top == null) { noMatch++; continue; }
                } else {
                    top = frames.get(0);
                }

                total++;
                counts.merge(frameKey(top), 1L, Long::sum);
            }
        }

        System.out.printf("CPU Hotspots  [%s]%n", file.getFileName());
        System.out.printf("Total samples matched: %d%n", total);
        if (threadPattern != null) System.out.printf("Thread filter:  %s  (%d events excluded)%n", threadPattern, filtered);
        if (packagePrefix  != null) System.out.printf("Package filter: %s  (%d events had no frame in package)%n",
                rawPkg, noMatch);
        System.out.println();

        if (counts.isEmpty()) { System.out.println("No samples matched."); return; }

        List<Map.Entry<String, Long>> sorted = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toList());

        int limit = Math.min(20, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            System.out.printf("  %5.1f%%  %4d  %s%n",
                100.0 * entry.getValue() / total, entry.getValue(), entry.getKey());
        }
        if (sorted.size() > limit) {
            System.out.printf("  ... and %d more method(s)%n", sorted.size() - limit);
        }
    }

    static String internalName(RecordedFrame f) {
        return f.getMethod().getType().getName().replace('.', '/');
    }

    static String frameKey(RecordedFrame f) {
        String cls  = f.getMethod().getType().getName().replace('/', '.');
        String meth = f.getMethod().getName();
        int    line = f.getLineNumber();
        return cls + "." + meth + (line > 0 ? ":" + line : "");
    }

    static String argValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
