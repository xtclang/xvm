/**
 * A callback that is invoked once in a test fixture after any test is invoked.
 */
interface AfterAllCallback
        extends Extension {

    @Override
    @RO Boolean requiresTarget.get() = False;

    /**
     * Called once for a test fixture after all tests have been invoked in that fixture and any
     * children of that fixture.
     *
     * If this method throws an exception, the remaining "after" test processing will continue to be
     * executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void afterAll(ExecutionContext context);
}
