/**
 * The `executor` package contains classes responsible for controlling
 * execution of test fixtures.
 */
package executor {
    /**
     * A skipped test result.
     */
	const SkipResult(Boolean skipped, String reason = "unknown") {
	    static SkipResult NotSkipped = new SkipResult(False);
    }
}