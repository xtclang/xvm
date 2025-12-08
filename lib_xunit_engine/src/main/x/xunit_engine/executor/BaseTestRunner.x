import extensions.EngineExtension;

/**
 * A base type for test runners.
 */
@Abstract service BaseTestRunner {
    /**
     * Execute tests and display the results on the console.
     *
     * @param testModule  the `Module` containing the tests to execute
     */
    Boolean run(Module testModule) {
        TestConfiguration.Builder builder        = createTestConfigurationBuilder(testModule);
        TestConfiguration         testConfig     = configureTestEngine(builder).build();
        StatusListener            statusListener = new StatusListener();
        ExecutionListener[]       listeners      = new Array();

        listeners.add(new StatusListener());

        ExecutionListener? runnerListener = createExecutionListener(testModule);
        if (runnerListener.is(ExecutionListener)) {
            listeners.add(runnerListener);
        }

        for (EngineExtension extension : testConfig.extensions) {
            extension.init();
            listeners.add(extension);
        }

        ExecutionListener listener = new CompositeExecutionListener(listeners.freeze(True));
        TestEngine        engine   = createTestEngine(testConfig, listener);
        engine.execute();
        return statusListener.success;
    }

    /**
     * Create a new `TestConfiguration.Builder` instance.
     *
     * @param testModule  the `Module` containing the tests to execute
     *
     * @return the new `TestConfiguration.Builder` instance
     */
    TestConfiguration.Builder createTestConfigurationBuilder(Module testModule) {
        DiscoveryConfiguration discoveryConfig = createDiscoveryConfiguration(testModule);
        ExecutionConfiguration executionConfig = createExecutionConfiguration(testModule);
        return TestConfiguration.builder()
                .discoverExtensions(testModule)
                .withDiscoveryConfig(discoveryConfig)
                .withExecutionConfig(executionConfig);
    }

    /**
     * Create a new `TestEngine` instance.
     *
     * @param config    the `TestConfiguration` to use
     * @param listener  the `ExecutionListener` to use
     *
     * @return the new `TestEngine` instance
     */
    TestEngine createTestEngine(TestConfiguration config, ExecutionListener listener) {
        return new DefaultTestEngine(config, listener);
    }

    /**
     * Apply any custom configuration to the `TestConfiguration.Builder`.
     *
     * @param builder  the `TestConfiguration.Builder` to configure
     *
     * @return the configured `TestConfiguration.Builder`
     */
    TestConfiguration.Builder configureTestEngine(TestConfiguration.Builder builder) {
        return builder;
    }

    /**
     * Create an optional `ExecutionListener` instance.
     *
     * @param testModule  the `Module` containing the tests to execute
     *
     * @return the new `ExecutionListener` instance, or `Null` if no listener is required
     */
    ExecutionListener? createExecutionListener(Module testModule) {
        return Null;
    }

    /**
     * Create a `DiscoveryConfiguration` instance.
     *
     * @param testModule  the `Module` containing the tests to execute
     *
     * @return the new `DiscoveryConfiguration` instance
     */
    DiscoveryConfiguration createDiscoveryConfiguration(Module testModule) {
        return DiscoveryConfiguration.builder()
                .autoConfigure(testModule)
                .build();
    }

    /**
     * Create an `ExecutionConfiguration` instance.
     *
     * @param testModule  the `Module` containing the tests to execute
     *
     * @return the new `ExecutionConfiguration` instance
     */
    ExecutionConfiguration createExecutionConfiguration(Module testModule) {
        return ExecutionConfiguration.create();
    }

    /**
     * A simple `ExecutionListener` that tracks the overall test status.
     */
    service StatusListener
            implements ExecutionListener {

        /**
         * `True` if all tests passed or `False` if any test failed or caused an error.
         */
        Boolean success = True;

        @Override
    	void onCompleted(Model model, Result result) {
            success = success && result.status.success;
        }
    }
}