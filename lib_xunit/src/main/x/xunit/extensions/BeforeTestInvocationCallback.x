/**
 * A callback that is invoked repeatedly directly before each test is invoked, but after
 * any `BeforeEachCallback` extensions and after any `@BeforeEach` annotated methods.
 *
 * Extensions such as `BeforeTestInvocationCallback` are typically executed in the order they are
 * discovered Ordering can be determined by overriding the `Extension.priority` property to specify
 * an `Int` priority value. Before extensions with the higher `priority` will be first.
 */
interface BeforeTestInvocationCallback
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
    void beforeTest(ExecutionContext context);
}
