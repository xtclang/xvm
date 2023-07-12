/**
 * A callback that is invoked once in a test fixture before any test is invoked.
 *
 * Extensions such as `BeforeCallback` are typically executed in the order they are discovered.
 * Ordering can be determined by overriding the `Extension.priority` property to specify an `Int`
 * priority value. Before extensions with the higher `priority` will be first.
 */
interface BeforeAllCallback
        extends Extension {
    /**
     * Called once for a test fixture before any test is invoked.
     *
     * If this method throws an exception, no further "before" test processing will
     * be executed, the test will not execute, but all "after" test processing will
     * be executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void beforeAll(ExecutionContext context);
}
