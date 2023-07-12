import xunit_engine.discovery.Selector;
import xunit_engine.discovery.selectors;

service ConsoleRunner {
    /**
     * Execute tests using the console runner.
     *
     * @param testModule  the `Module` containing the tests to execute
     */
    void run(Module testModule) {
        ConsoleExecutionListener listener        = new ConsoleExecutionListener();
        DiscoveryConfiguration   discoveryConfig = createDiscoveryConfiguration(testModule);
        ExecutionConfiguration   executionConfig = ExecutionConfiguration.create();
        TestConfiguration        testConfig      = TestConfiguration.builder()
                                                        .withDiscoveryConfig(discoveryConfig)
                                                        .withExecutionConfig(executionConfig)
                                                        .build();

        DefaultTestEngine engine = new DefaultTestEngine(testConfig, listener);
        engine.execute();
    }

    private DiscoveryConfiguration createDiscoveryConfiguration(Module testModule) {
        assert Selector selector := selectors.forModule(testModule);
        return DiscoveryConfiguration.builder()
                .withSelector(selector)
                .build();
    }
}