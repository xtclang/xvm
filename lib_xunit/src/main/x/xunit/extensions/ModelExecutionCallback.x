/**
 * A callback that is called by the test engine for before and after execution of each test fixture.
 * A test fixture is any module, class, package or test method in the hierarchy of tests being
 * executed.
 */
interface FixtureExecutionCallback
        extends Extension {
    /**
     * Called before any tests are executed for a test fixture.
     */
    void beforeFixtureExecution(ExecutionContext context) {
    }

    /**
     * Called after all tests have been executed for a test fixture.
     */
    void afterFixtureExecution(ExecutionContext context) {
    }
}
