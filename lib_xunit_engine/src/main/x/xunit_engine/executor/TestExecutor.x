import extensions.ExtensionRegistry;

import xunit.SkipResult;

import xunit.extensions.Extension;
import xunit.extensions.ExtensionProvider;
import xunit.extensions.ResourceLookupCallback;

/**
 * An executor of tests.
 */
const TestExecutor {
    /**
     * Create a `TestExecutor`.
     *
     * @param model          the `Model` that defines the tests to execute
     * @param configuration  the `ExecutionConfiguration` to configure execution
     */
    construct (Model model, ExecutionConfiguration configuration) {
        this.model         = model;
        this.lifecycle     = model.createExecutionLifecycle();
        this.configuration = configuration;
    }

    /**
     * The `Model` that defines the tests to execute.
     */
    public/private Model model;

    /**
     * The `ExecutionConfiguration` to configure execution.
     */
    public/private ExecutionConfiguration configuration;

    /**
     * The current `ExecutionLifecycle`.
     */
    public/private ExecutionLifecycle lifecycle;

    /**
     * Execute the tests in this executor's `Model` including recursively
     * executing tests in any child `Model's.
     *
     * @param parentContext   the parent `EngineExecutionContext` to use
     * @param parentRegistry  an optional parent `ExtensionRegistry` to use
     * @param extensions      an optional array of `Extension`s to use
     */
    void execute(EngineExecutionContext parentContext,
                 ExtensionRegistry?     parentRegistry = Null,
                 Extension[]            extensions     = [])
     {
        @Inject Clock          clock;
        ExtensionRegistry      registry   = new ExtensionRegistry(model, parentRegistry);
        ExceptionCollector     collector  = new ExceptionCollector();
        SkipResult             skipResult = NotSkipped;
        Boolean                started    = False;
        Time                   start      = clock.now;
        Duration               duration   = None;
        EngineExecutionContext context    = parentContext;

        registry.addAll(extensions);
        updateResourceProvider(context, registry);

		if (context := collector.execute(() -> lifecycle.prepare(parentContext, registry))) {
            updateResourceProvider(context, registry);
            if (skipResult := collector.execute(() -> lifecycle.shouldBeSkipped(context, registry))) {
                if (collector.empty && !skipResult.skipped) {
                    if (collector.executeVoid(() -> context.listener.onStarted(model))) {
                        started = True;
                        if (context := executeRecursively(collector, context, registry)) {
                            updateResourceProvider(context, registry);
                            cleanUp(collector, context, registry);
                            duration = clock.now - start;
                        }
                    }
                }
            }
        }
        updateResourceProvider(context, registry);
        reportCompletion(collector, parentContext, registry, skipResult, started, duration);
    }

    /**
     * Recursively execute the test in any child models.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param registry  the `ExtensionRegistry` containing any test extensions
     *
     * @return True iff test execution can continue
     * @return the `EngineExecutionContext` to use for further test executions
     */
    private conditional EngineExecutionContext executeRecursively(ExceptionCollector collector,
            EngineExecutionContext context, ExtensionRegistry registry) {

        return collector.execute(() -> {
            updateResourceProvider(context, registry);

            EngineExecutionContext ctx = lifecycle.before(collector, context, registry);
            updateResourceProvider(ctx, registry);
            if (collector.empty) {
                ctx = lifecycle.execute(collector, ctx, registry);
                updateResourceProvider(ctx, registry);
                for (Model child : lifecycle.getChildren(ctx)) {
                    TestExecutor executor = new TestExecutor(child, configuration);
                    executor.execute(ctx, registry);
                }
                collector.executeVoid(() -> lifecycle.after(collector, ctx, registry));
            }
            return ctx;
        });
    }

    /**
     * Clean up the test execution.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param registry  the `ExtensionRegistry` containing any test extensions
     */
    private void cleanUp(ExceptionCollector     collector,
                         EngineExecutionContext context,
                         ExtensionRegistry      registry) {
        updateResourceProvider(context, registry);
        collector.executeVoid(() -> lifecycle.cleanUp(collector, context, registry));
    }

    /**
     * Report completion of test execution.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param registry  the `ExtensionRegistry` containing any test extensions
     * @param skipResult  an optional `SkipResult` if a test was skipped
     * @param started     a flag to indicate whether test execution was started
     * @param duration    the duration the test took to execute
     */
    private Result reportCompletion(ExceptionCollector     collector,
                                    EngineExecutionContext context,
                                    ExtensionRegistry      registry,
                                    SkipResult             skipResult,
                                    Boolean                started,
                                    Duration               duration) {

        updateResourceProvider(context, registry);

        Result result;
        if (collector.empty && skipResult.skipped) {
            result = collector.result.withDuration(duration);
            try {
                lifecycle.onSkipped(context, registry, skipResult);
            }
            catch (Exception e) {
                @Inject Console console;
                console.print($"{e}");
            }
            context.listener.onSkipped(model, skipResult.reason);
        } else {
            if (!started) {
                context.listener.onStarted(model);
            }
            result = collector.result.withDuration(duration);
            try {
                lifecycle.onCompleted(context, registry, result);
            }
            catch (Exception e) {
                @Inject Console console;
                console.print($"{e}");
            }
            context.listener.onCompleted(model, result);
        }
        context.onCompleted(result);
        return result;
    }

    private void updateResourceProvider(EngineExecutionContext context, ExtensionRegistry registry) {
        ResourceLookupCallback[]     callbacks = registry.get(ResourceLookupCallback, True);
        @Inject TestResourceProvider provider;
        provider.setExecutionContext(context, callbacks.freeze(True));
    }
}