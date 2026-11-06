package com.codecritic.analysis.impl;

import com.codecritic.analysis.BugDetector;
import com.codecritic.dto.BugFinding;
import com.codecritic.metrics.AnalysisMetrics;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caching decorator for the expensive SpotBugs pass.
 *
 * <p>SpotBugs compiles the submitted source and runs an external subprocess, which
 * dominates analysis latency. Re-analyzing identical source (e.g. the same snippet
 * in consecutive reviews, or repeated files in a repository run) replays the cached
 * result instead of recompiling.</p>
 *
 * <p>Cache key = SHA-256 of the source plus a detector version. Bumping
 * {@link #DETECTOR_VERSION} invalidates all entries whenever detection logic
 * changes. The cache is bounded to cap memory usage on long-running servers.</p>
 */
@Component
public class CachedSpotBugsBugDetector implements BugDetector {

    private static final String DETECTOR_VERSION = "1";
    private static final int MAX_ENTRIES = 256;

    private final SpotBugsBugDetector delegate;
    private final AnalysisMetrics metrics;
    private final Map<String, List<BugFinding>> cache;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    /**
     * Creates a detector that caches results from the supplied delegate.
     *
     * @param delegate underlying SpotBugs detector whose runs are cached
     * @param metrics metrics sink notified for each actual SpotBugs execution
     */
    public CachedSpotBugsBugDetector(SpotBugsBugDetector delegate, AnalysisMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            /**
             * Evicts the least-recently-used entry once the cache exceeds its bound.
             *
             * @param eldest least-recently-used entry
             * @return {@code true} when the entry should be removed
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<BugFinding>> eldest) {
                return size() > MAX_ENTRIES;
            }
        });
    }

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<List<BugFinding>>> inFlight = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Detects bugs, replaying a cached result for previously analyzed source.
     *
     * <p>Concurrent requests for the same source share a single underlying
     * execution; only the first caller runs SpotBugs while the others wait for
     * its result.</p>
     *
     * @param code source code to analyze
     * @return findings from the cache or the underlying detector
     */
    @Override
    public List<BugFinding> detect(String code) {
        String key = cacheKey(code);
        synchronized (cache) {
            List<BugFinding> cached = cache.get(key);
            if (cached != null) {
                hits.incrementAndGet();
                return cached;
            }
        }
        misses.incrementAndGet();

        java.util.concurrent.CompletableFuture<List<BugFinding>> future = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<List<BugFinding>> existing = inFlight.putIfAbsent(key, future);

        if (existing != null) {
            try {
                return existing.get();
            } catch (Exception e) {
                throw new RuntimeException("Concurrent SpotBugs execution failed", e);
            }
        }

        try {
            long started = System.nanoTime();
            List<BugFinding> findings = delegate.detect(code);
            metrics.recordSpotBugsRun((System.nanoTime() - started) / 1_000_000);
            synchronized (cache) {
                cache.put(key, findings);
            }
            future.complete(findings);
            return findings;
        } catch (Exception e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key);
        }
    }

    /**
     * Number of cached results replayed.
     *
     * @return the number of cache hits
     */
    public long hits() {
        return hits.get();
    }

    /**
     * Number of underlying SpotBugs executions performed.
     *
     * @return the number of cache misses
     */
    public long misses() {
        return misses.get();
    }

    /**
     * Current number of cached entries.
     *
     * @return the cached entry count
     */
    public int size() {
        return cache.size();
    }

    /**
     * Builds the cache key from the source and detector version.
     *
     * @param code source code whose key is computed
     * @return the hexadecimal SHA-256 digest used as the cache key
     * @throws IllegalStateException if SHA-256 is unavailable
     */
    static String cacheKey(String code) {
        try {
            String material = (code == null ? "" : code) + "|" + DETECTOR_VERSION;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
