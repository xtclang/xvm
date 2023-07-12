/**
 * A callback that is invoked repeatedly after each test in a test fixture.
 *
 * Extensions such as `AfterEachCallback` are typically executed in the order they are discovered.
 * Ordering can be determined by overriding the `Extension.priority` property to specify an `Int`
 * priority value. After extensions with the lower `priority` will be first.
 */
interface AfterEachCallback
        extends Extension {
    /**
     * Called repeatedly after each test in a test fixture.
     *
     * If this method throws an exception, the remaining "after" test processing will
     * continue to be executed.
     *
     * @param context  the current `ExecutionContext`
     */
    void afterEach(ExecutionContext context);
}
