/**
 * The result of a test fixture execution.
 *
 * @param status      the final result status
 * @param message     any message associated with the result
 * @param exception   any `Exception` thrown by the test execution
 * @param suppressed  any additional suppressed exceptions, for example thrown
 *                    during test cleanup
 */
const Result(Status status, Exception? exception = Null, Exception[]? suppressed = Null,
        Duration duration = Duration.None) {
    /**
     * Create a copy of this `Result` with a specific duration.
     *
     * @param d  the result `Duration`
     *
     * @return a copy of this `Result` with a specific duration
     */
    Result withDuration(Duration d) {
        return new Result(status, exception, suppressed, d);
    }

    /**
     * A singleton `Result` representing a successful test fixture.
     */
    static Result Passed = new Result(Successful);

    /**
     * The status of a test result.
     */
    enum Status {
        /**
         * Execution was successful.
         */
        Successful,
        /**
         * Execution was aborted, i.e. execution started but not finished.
         */
        Aborted,
        /**
         * Execution failed.
         */
        Failed,
        /**
         * Execution skipped.
         */
        Skipped,
    }
}