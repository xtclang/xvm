/**
 * A callback that is invoked repeatedly directly after each test is invoked, but before any
 * `AfterEachCallback` extensions and before any `@AfterEach` annotated functions or methods.
 */
interface AfterTestInvocationCallback
        extends Extension {
    /**
     * Called once for a test fixture after any test is invoked.
     *
     * If this method throws an exception, any remaining "after" test processing will still be
     * executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void afterTest(ExecutionContext context);
}
