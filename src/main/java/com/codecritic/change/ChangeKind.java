package com.codecritic.change;

/**
 * Classifies how a file changed between the base and head revisions of a comparison.
 */
public enum ChangeKind {
    /** The file exists only at the head revision. */
    ADDED,
    /** The file exists at both revisions and its content changed. */
    MODIFIED,
    /** The file exists only at the base revision. */
    DELETED,
    /** The file was moved from its base path to a different head path. */
    RENAMED
}
