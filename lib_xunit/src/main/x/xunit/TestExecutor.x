/**
 * A test executor will execute tests in a `Module`.
 *
 * A test is typically a method annotated with `@Test`.
 */
interface TestExecutor {
    /**
     * Discover and run `@Test` annotated methods in a `Module`.
     *
     * @param mod  the `Module` to run tests in
     */
    void runTests(Module mod);
}
