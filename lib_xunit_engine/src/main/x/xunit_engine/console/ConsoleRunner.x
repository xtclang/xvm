import xunit_engine.discovery.Selector;
import xunit_engine.discovery.selectors;

/**
 * An XUnit test runner that executes tests and prints the results to the console.
 */
service ConsoleRunner {
    /**
     * Execute tests and display the results on the console.
     *
     * @param testModule  the `Module` containing the tests to execute
     */
    Int run(Module testModule) {
        ConsoleExecutionListener listener        = new ConsoleExecutionListener();
        DiscoveryConfiguration   discoveryConfig = createDiscoveryConfiguration(testModule);
        ExecutionConfiguration   executionConfig = ExecutionConfiguration.create();
        TestConfiguration        testConfig      = TestConfiguration.builder()
                                                        .withDiscoveryConfig(discoveryConfig)
                                                        .withExecutionConfig(executionConfig)
                                                        .build();

        DefaultTestEngine engine = new DefaultTestEngine(testConfig, listener);
        engine.execute();
        return listener.success ? 0 : 1;
    }

    private DiscoveryConfiguration createDiscoveryConfiguration(Module testModule) {
        return DiscoveryConfiguration.builder()
                .autoConfigure(testModule)
                .build();
    }
}