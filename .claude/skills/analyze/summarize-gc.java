import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Reads GC-related events from a JFR file and prints a compact GC summary:
 * collection count, pause stats, heap usage, allocation rate, and GC causes.
 *
 * Usage: java summarize-gc.java <jfr-file>
 *
 * GC events are process-wide; thread/package hints do not apply.
 */
class SummarizeGc {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java summarize-gc.java <jfr-file>");
            System.exit(1);
        }

        Path file = Path.of(args[0]);

        int gcCount       = 0;
        long totalPauseNs = 0;
        long maxPauseNs   = 0;
        Map<String, Integer> causeCounts = new LinkedHashMap<>();

        // Heap snapshots keyed by gcId
        Map<Long, Long> heapBefore = new LinkedHashMap<>();
        Map<Long, Long> heapAfter  = new LinkedHashMap<>();

        Instant firstEvent = null;
        Instant lastEvent  = null;

        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();

                Instant t = e.getStartTime();
                if (firstEvent == null || t.isBefore(firstEvent)) firstEvent = t;
                if (lastEvent  == null || t.isAfter(lastEvent))   lastEvent  = t;

                switch (e.getEventType().getName()) {
                    case "jdk.GarbageCollection": {
                        gcCount++;
                        Duration d  = e.getDuration("duration");
                        long     ns = d.toNanos();
                        totalPauseNs += ns;
                        if (ns > maxPauseNs) maxPauseNs = ns;
                        String cause = e.getString("cause");
                        if (cause != null) causeCounts.merge(cause, 1, Integer::sum);
                        break;
                    }
                    case "jdk.GCHeapSummary": {
                        long   gcId = e.getLong("gcId");
                        long   used = e.getLong("heapUsed");
                        String when = e.getString("when");
                        if ("Before GC".equals(when)) heapBefore.put(gcId, used);
                        else if ("After GC".equals(when)) heapAfter.put(gcId, used);
                        break;
                    }
                }
            }
        }

        if (gcCount == 0) {
            System.out.printf("GC Summary  [%s]%n%n", file.getFileName());
            System.out.println("No GC events found in this recording.");
            return;
        }

        // Recording duration
        double durationSec = (firstEvent != null && lastEvent != null && !firstEvent.equals(lastEvent))
            ? Duration.between(firstEvent, lastEvent).toMillis() / 1000.0 : 1.0;

        // Pause stats
        double gcFreq      = gcCount / durationSec;
        double avgPauseMs  = (totalPauseNs / 1_000_000.0) / gcCount;
        double maxPauseMs  = maxPauseNs / 1_000_000.0;
        double totalPauseMs = totalPauseNs / 1_000_000.0;
        double pausePct    = (totalPauseNs / 1_000_000_000.0) / durationSec * 100.0;

        // Heap and allocation stats
        long totalReclaimed  = 0;
        long sumHeapBefore   = 0;
        long sumHeapAfter    = 0;
        int  heapSamples     = 0;

        for (long gcId : heapBefore.keySet()) {
            if (heapAfter.containsKey(gcId)) {
                long before = heapBefore.get(gcId);
                long after  = heapAfter.get(gcId);
                totalReclaimed += Math.max(0, before - after);
                sumHeapBefore  += before;
                sumHeapAfter   += after;
                heapSamples++;
            }
        }

        long avgBefore  = heapSamples > 0 ? sumHeapBefore / heapSamples : 0;
        long avgAfter   = heapSamples > 0 ? sumHeapAfter  / heapSamples : 0;
        long avgReclaim = heapSamples > 0 ? totalReclaimed / heapSamples : 0;
        double allocMbSec = durationSec > 0 ? (totalReclaimed / 1_000_000.0) / durationSec : 0;

        // Output
        System.out.printf("GC Summary  [%s]%n", file.getFileName());
        System.out.printf("Recording duration:    %.1f s%n%n", durationSec);

        System.out.printf("Collections:           %d  (%.1f/sec)%n", gcCount, gcFreq);
        System.out.printf("Avg pause:             %.2f ms%n", avgPauseMs);
        System.out.printf("Max pause:             %.2f ms%n", maxPauseMs);
        System.out.printf("Total pause time:      %.1f ms  (%.1f%% of recording)%n%n", totalPauseMs, pausePct);

        System.out.printf("Allocation rate:       %.0f MB/s%n", allocMbSec);
        System.out.printf("Avg heap before GC:    %s%n", formatBytes(avgBefore));
        System.out.printf("Avg heap after GC:     %s%n", formatBytes(avgAfter));
        System.out.printf("Avg reclaimed/cycle:   %s%n%n", formatBytes(avgReclaim));

        System.out.println("GC causes:");
        causeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> System.out.printf("  %4d  %s%n", entry.getValue(), entry.getKey()));
    }

    static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format("%.1f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L)     return String.format("%.1f MB", bytes / 1_000_000.0);
        if (bytes >= 1_000L)         return String.format("%.1f kB", bytes / 1_000.0);
        return bytes + " B";
    }
}
