/**
 * The executor package contains types used during test execution.
 */
package executor {
    /**
     * Execute any tests in the specified module using a ConsoleRunner.
     *
     * @return True iif all the tests succeeded or False if there was a test failure or other error
     */
    Boolean runTests(Module m) {
        console.ConsoleRunner runner = new console.ConsoleRunner();
        return runner.run(m);
    }
}
