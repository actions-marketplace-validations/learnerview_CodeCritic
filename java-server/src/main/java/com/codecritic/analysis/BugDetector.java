package com.codecritic.analysis;

import com.codecritic.dto.BugFinding;

import java.util.List;

/**
 * Strategy contract for bug detection. Implementations scan Java source and
 * return zero or more findings.
 */
public interface BugDetector {

    /**
     * Scans Java source for potential defects.
     *
     * @param code source code to inspect
     * @return findings in source order, or an empty list when none are detected
     */
    List<BugFinding> detect(String code);
}
