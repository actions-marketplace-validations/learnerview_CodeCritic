package com.codecritic.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory metrics for the analysis service.
 *
 * <p>Tracks request counts and latency per analysis type (complexity, bugs,
 * test-generation) plus SpotBugs execution statistics. Snapshot is a plain
 * {@code Map} so it can be exposed over HTTP without extra dependencies.</p>
 */
@Component
public class AnalysisMetrics {

    /**
     * Creates an empty metrics registry with no recorded observations.
     */
    public AnalysisMetrics() {
    }

    private final ConcurrentHashMap<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TotalCount> latencies = new ConcurrentHashMap<>();
    private final AtomicLong spotBugsRuns = new AtomicLong();
    private final AtomicLong spotBugsTotalMs = new AtomicLong();

    /**
     * Records one completed analysis of the given type (for example
     * {@code "complexity"}).
     *
     * @param analysisType analysis category used as the metric key
     * @param latencyMs observed latency in milliseconds
     */
    public void record(String analysisType, long latencyMs) {
        requestCounts.computeIfAbsent(analysisType, k -> new AtomicLong()).incrementAndGet();
        latencies.computeIfAbsent(analysisType, k -> new TotalCount()).add(latencyMs);
    }

    /**
     * Records one SpotBugs execution with its wall-clock duration.
     *
     * @param durationMs duration of the execution in milliseconds
     */
    public void recordSpotBugsRun(long durationMs) {
        spotBugsRuns.incrementAndGet();
        spotBugsTotalMs.addAndGet(durationMs);
    }

    /**
     * Builds a point-in-time, JSON-friendly snapshot of the metrics.
     *
     * <p>The snapshot includes a timestamp, per-analysis request counts and
     * average latency, and SpotBugs run statistics.</p>
     *
     * @return an immutable-style nested map of metric names to values
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new TreeMap<>();
        out.put("startedAtEpochMs", System.currentTimeMillis());
        Map<String, Object> byType = new TreeMap<>();
        for (Map.Entry<String, AtomicLong> entry : requestCounts.entrySet()) {
            TotalCount tc = latencies.get(entry.getKey());
            byType.put(entry.getKey(), Map.of(
                    "requests", entry.getValue().get(),
                    "avgLatencyMs", tc == null || tc.count() == 0 ? 0 : tc.total() / tc.count(),
                    "samples", tc == null ? 0 : tc.count()));
        }
        out.put("analysis", byType);
        long runs = spotBugsRuns.get();
        out.put("spotbugs", Map.of(
                "runs", runs,
                "totalMs", spotBugsTotalMs.get(),
                "avgMs", runs == 0 ? 0 : spotBugsTotalMs.get() / runs));
        return out;
    }

    private static final class TotalCount {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong count = new AtomicLong();

        void add(long value) {
            total.addAndGet(value);
            count.incrementAndGet();
        }

        long total() {
            return total.get();
        }

        long count() {
            return count.get();
        }
    }
}
