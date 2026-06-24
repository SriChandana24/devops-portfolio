package com.example

/**
 * Holds and validates deployment configuration.
 *
 * Demonstrates the src/ pattern:
 *   - full Groovy class organized by package
 *   - unit-testable in isolation (no Jenkins context needed)
 *   - implements Serializable so Jenkins can checkpoint pipeline state
 *
 * Usage from a vars/ step:
 *   def cfg = new com.example.DeployConfig(args)
 *   cfg.validate()
 */
class DeployConfig implements Serializable {

    private static final long serialVersionUID = 1L
    private static final List REQUIRED = ['chart', 'releaseName', 'namespace']

    String chart
    String releaseName
    String namespace
    String valuesFile = 'values.yaml'
    String timeout    = '5m'
    Boolean approve

    DeployConfig(Map args) {
        args.each { k, v -> this[k] = v }
    }

    void validate() {
        def missing = REQUIRED.findAll { !this[it] }
        if (missing) {
            throw new IllegalArgumentException(
                "DeployConfig: missing required field(s): ${missing.join(', ')}"
            )
        }
    }

    boolean isProd() { namespace == 'prod' }
}
