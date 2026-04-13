package dev.sunwell.toy;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates a tag normalization and deduplication utility.
 * <p>
 * Contains two subtle performance problems:
 * 1. String.replaceAll() compiles its regex pattern on every call — a static
 * Pattern.compile() would pay that cost once.
 * 2. Duplicate detection walks the result list linearly for each candidate,
 * giving O(n²) behaviour — a LinkedHashSet would be O(n).
 */
public class CpuHog {

    /**
     * Returns a deduplicated, normalized copy of the tag list.
     * Tags are lowercased and stripped of characters outside [a-z0-9_-].
     */
    public List<String> deduplicateTags(List<String> tags) {
        List<String> result = new ArrayList<>();

        for (String tag : tags) {
            // Subtle bug #1: String.replaceAll compiles the regex on every call.
            // Production fix: private static final Pattern TAG_PATTERN =
            //     Pattern.compile("[^a-z0-9_-]");
            String normalized = tag.toLowerCase().replaceAll("[^a-z0-9_-]", "");

            if (normalized.isEmpty()) {
                continue;
            }

            // Subtle bug #2: linear scan of result list — O(n) per element → O(n²) total.
            // Production fix: use a LinkedHashSet to track seen values.
            boolean seen = false;
            for (String existing : result) {
                if (existing.equals(normalized)) {
                    seen = true;
                    break;
                }
            }

            if (!seen) {
                result.add(normalized);
            }
        }

        return result;
    }
}
