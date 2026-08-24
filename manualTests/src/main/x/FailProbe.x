/**
 * Deliberately failing module used to verify that runtime failures are machine-visible
 * (non-zero exit code) through every runner path. See docs/reentrancy/logging-diagnostics-audit.md
 * "Test pass/fail authority".
 */
module FailProbe {
    void run() {
        throw new IllegalState("deliberate failure probe");
    }
}
