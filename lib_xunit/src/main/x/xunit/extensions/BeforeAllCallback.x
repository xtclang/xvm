/**
 * A callback that is invoked once in a test fixture before any test is invoked.
 */
interface BeforeAllCallback
        extends Extension {

    @Override
    @RO Boolean requiresTarget.get() = False;

    /**
     * Called once for a test fixture before any test is invoked.
     *
     * If this method throws an exception, no further "before" test processing will be executed, the
     * test will not execute, but all "after" test processing will be executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void beforeAll(ExecutionContext context);
}
