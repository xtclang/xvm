/**
 * A callback that is invoked repeatedly after each test in a test fixture.
 */
interface AfterEachCallback
        extends Extension {
    /**
     * Called repeatedly after each test in a test fixture.
     *
     * If this method throws an exception, the remaining "after" test processing will continue to be
     * executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void afterEach(ExecutionContext context);
}
