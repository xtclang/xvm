import executor.EngineExecutionContext;
import executor.EngineExecutionContext.Builder;
import executor.TestExecutor;

/**
 * A default `TestEngine` implementation.
 */
service DefaultTestEngine
        implements TestEngine {

    /**
     * Create a default test engine.
     *
     * @param configuration  the `TestConfiguration` to use to configure the test discovery and execution
     * @param listener       an optional `ExecutionListener`
     */
    @Override
    construct (TestConfiguration configuration, ExecutionListener listener = ExecutionListener.NoOp) {
        this.configuration = configuration;
        this.listener      = listener;
    }

    /**
     * The `TestConfiguration` to use to configure the test discovery and execution.
     */
    private TestConfiguration configuration;

    /**
     * The `ExecutionListener` to receive test execution events.
     */
    private ExecutionListener listener;

    @Override
    void execute() {
        Model[] models = discover();
        execute(models);
    }

    /**
     * Discover test models.
     *
     * @return the discovered models
     */
    protected Model[] discover() {
        DiscoveryConfiguration config = configuration.discoveryConfig;
        DiscoveryEngine        engine = DiscoveryEngine.create();
        return engine.discover(config);
    }

    /**
     * Execute tests in the specified models.
     *
     * @param models  an array of `Model` instance containing the tests to execute
     */
    protected void execute(Model[] models) {
        ExecutionConfiguration executionConfig = configuration.executionConfig;
        for (Model model : models) {
            TestExecutor executor = new TestExecutor(model, executionConfig);
            EngineExecutionContext.Builder builder = modifyExecutionContext(EngineExecutionContext.builder(model));
            executor.execute(builder.build());
        }
    }

    /**
     * Apply any modifications to the `EngineExecutionContext` builder.
     *
     * @param builder  the `EngineExecutionContext.Builder` to modify
     *
     * @return the modified `EngineExecutionContext.Builder` to modify
     */
    protected EngineExecutionContext.Builder modifyExecutionContext(EngineExecutionContext.Builder builder) {
        return builder.withListener(listener);
    }
}
