/**
 * A callback that is invoked repeatedly, before every test is invoked.
 */
interface BeforeEachCallback
        extends Extension {
    /**
     * Called repeatedly before each test is invoked.
     *
     * If this method throws an exception, no further "before" test processing will be executed, the
     * test will not execute, but all "after" test processing will be executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void beforeEach(ExecutionContext context);
}
