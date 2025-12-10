/**
 * The result of a test fixture execution.
 *
 * @param status      the final result status
 * @param message     any message associated with the result
 * @param exception   any `Exception` thrown by the test execution
 * @param suppressed  any additional suppressed exceptions, for example thrown
 *                    during test cleanup
 */
const Result(Status       status,
             Exception?   exception  = Null,
             Exception[]? suppressed = Null,
             Duration     duration   = Duration.None,
             Int          count      = 1,
             Int          succeeded  = 0,
             Int          skipped    = 0,
             Int          failures   = 0,
             Int          errors     = 0) {
    /**
     * Create a copy of this `Result` with a specific duration.
     *
     * @param d  the result `Duration`
     *
     * @return a copy of this `Result` with a specific duration
     */
    Result withDuration(Duration d)
            = new Result(status, exception, suppressed, d, count, succeeded, skipped,
                         failures, errors);

    /**
     * A singleton `Result` representing a successful test fixture.
     */
    static Result Passed = new Result(Successful, count=1, succeeded=1);

    Result merge(Result result) {
        Status mergedStatus;
        if (status != Successful) {
            mergedStatus = status;
        } else {
            mergedStatus = result.status == Successful || result.status == Skipped
                    ? Successful : Failed;
        }
        return new Result(mergedStatus, exception, suppressed,
                          duration  + result.duration,
                          count     + result.count,
                          succeeded + result.succeeded,
                          skipped   + result.skipped,
                          failures  + result.failures,
                          errors    + result.errors);
    }

    /**
     * The status of a test result.
     */
    enum Status(Boolean success) {
        /**
         * Execution was successful.
         */
        Successful(True),
        /**
         * Execution was aborted, i.e. execution started but not finished.
         */
        Aborted(False),
        /**
         * Execution failed.
         */
        Failed(False),
        /**
         * Execution skipped.
         */
        Skipped(True),
    }
}