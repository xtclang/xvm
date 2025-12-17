/**
 * A callback that wraps a single test execution.
 */
interface AroundTestCallback
        extends Extension {

    /**
     * Called before execution of the wrapped test.
     *
     * This method will be called before any `@BeforeEach` annotated methods and before any
     * `BeforeEachCallback` extensions.
     */
    void beforeTest(ExecutionContext context);

    /**
     * Called after execution of the wrapped test.
     *
     * This method will be called after any `@AfterEach` annotated methods and after any
     * `AfterEachCallback` extensions.
     */
    void afterTest(ExecutionContext context);
}
