package com.codecritic.exec;

/**
 * Strategy used to decide which project modules are tested.
 */
public enum TestSelectionMode {
    /**
     * Test only the modules affected by the changed paths and the modules that depend on them.
     */
    MODULES,
    /**
     * Test the full suite regardless of the changed paths.
     */
    ALL
}
