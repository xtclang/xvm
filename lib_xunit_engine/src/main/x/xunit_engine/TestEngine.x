/**
 * A `TestEngine` is the main service of XUnit and is responsible
 * for the discovery and execution of tests.
 */
interface TestEngine {

    /**
     * Create a `TestEngine`
     *
     * @param configuration  the configuration to control the engine execution
     * @param listener       the execution listener
     */
    construct (TestConfiguration configuration, ExecutionListener listener);
    
    /**
     * Discover and execute tests.
     */
    void execute();
}
