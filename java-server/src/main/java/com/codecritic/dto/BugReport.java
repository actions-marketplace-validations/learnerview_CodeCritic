package com.codecritic.dto;

import java.util.List;

/**
 * Result of a bug-detection run.
 *
 * @param bugs findings discovered in the analysed source; empty when no problems were found
 */
public record BugReport(List<BugFinding> bugs) { }
