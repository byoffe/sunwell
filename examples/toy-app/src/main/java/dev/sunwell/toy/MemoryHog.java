package dev.sunwell.toy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simulates a metric report builder.
 *
 * Contains two subtle performance problems:
 *  1. Builds the value listing by concatenating String objects in a loop,
 *     creating a new String (and discarding the old one) on every iteration.
 *     A StringBuilder would produce a single allocation.
 *  2. Defensively copies and sorts the input list on every call, even though
 *     neither the copy nor the sort result escapes the method.
 */
public class MemoryHog {

    /**
     * Returns a human-readable summary report for the given metric values.
     */
    public String buildReport(List<Integer> values) {
        // Subtle bug #1: defensive copy — the caller's list is never modified,
        // so this allocation is pure waste.
        List<Integer> data = new ArrayList<>(values);

        // Subtle bug #2: another defensive copy just to sort, discarded after use.
        List<Integer> sorted = new ArrayList<>(data);
        Collections.sort(sorted);

        // Subtle bug #3: String concatenation in a hot loop.
        // Each iteration allocates a new String and throws the previous one away.
        // Production fix: StringBuilder sb = new StringBuilder(); sb.append(...);
        String valueList = "";
        for (int i = 0; i < sorted.size(); i++) {
            valueList = valueList + sorted.get(i);
            if (i < sorted.size() - 1) {
                valueList = valueList + ",";
            }
        }

        long sum = 0;
        for (Integer v : data) {   // unboxing on every iteration
            sum += v;
        }
        double avg = data.isEmpty() ? 0.0 : (double) sum / data.size();
        int min = sorted.isEmpty() ? 0 : sorted.get(0);
        int max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);

        return "Report[n=" + data.size()
                + ", min=" + min
                + ", max=" + max
                + ", avg=" + String.format("%.2f", avg)
                + ", values=[" + valueList + "]]";
    }
}
