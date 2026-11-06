package com.codecritic.change;

/**
 * Classifies how a method or constructor changed between two revisions.
 */
public enum MethodChangeKind {
    /** The callable exists only at the head revision. */
    ADDED,
    /** The callable exists only at the base revision. */
    REMOVED,
    /** The callable exists at both revisions and only its body changed. */
    MODIFIED,
    /** The callable exists at both revisions and its signature changed. */
    SIGNATURE_CHANGED
}
