import extensions.ExtensionRegistry;

import xunit.SkipResult;

import xunit.extensions.Extension;
import xunit.extensions.ExtensionProvider;

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
     * @param parentContext     the parent `EngineExecutionContext` to use
     * @param parentExtensions  an optional parent `ExtensionRegistry` to use
     */
    void execute(EngineExecutionContext parentContext, ExtensionRegistry? parentExtensions = Null)
     {
        @Inject Clock          clock;
        ExtensionRegistry      extensions = new ExtensionRegistry(model, parentExtensions);
        ExceptionCollector     collector  = new ExceptionCollector();
        SkipResult             skipResult = NotSkipped;
        Boolean                started    = False;
        Time                   start      = clock.now;
        Duration               duration   = None;
        EngineExecutionContext context    = parentContext;

        @Inject TestResourceProvider provider;
        provider.setExecutionContext(context);

		if (context := collector.execute(() -> lifecycle.prepare(parentContext, extensions))) {
            provider.setExecutionContext(context);
            if (skipResult := collector.execute(() -> lifecycle.shouldBeSkipped(context, extensions))) {
                if (collector.empty && !skipResult.skipped) {
                    if (collector.executeVoid(() -> context.listener.onStarted(model))) {
                        started = True;
                        if (context := executeRecursively(collector, context, extensions)) {
                            provider.setExecutionContext(context);
                            cleanUp(collector, context, extensions);
                            duration = clock.now - start;
                        }
                    }
                }
            }
        }
        provider.setExecutionContext(context);
        reportCompletion(collector, parentContext, extensions, skipResult, started, duration);
    }

    /**
     * Recursively execute the test in any child models.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param extensions  the `ExtensionRegistry` containing any test extensions
     *
     * @return True iff test execution can continue
     * @return the `EngineExecutionContext` to use for further test executions
     */
    private conditional EngineExecutionContext executeRecursively(ExceptionCollector collector,
            EngineExecutionContext context, ExtensionRegistry extensions) {

        return collector.execute(() -> {
            @Inject TestResourceProvider provider;
            provider.setExecutionContext(context);

            EngineExecutionContext ctx = lifecycle.before(collector, context, extensions);
            provider.setExecutionContext(ctx);
            if (collector.empty) {
                ctx = lifecycle.execute(collector, ctx, extensions);
                provider.setExecutionContext(ctx);
                for (Model child : lifecycle.getChildren(ctx)) {
                    TestExecutor executor = new TestExecutor(child, configuration);
                    executor.execute(ctx, extensions);
                }
                collector.executeVoid(() -> lifecycle.after(collector, ctx, extensions));
            }
            return ctx;
        });
    }

    /**
     * Clean up the test execution.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param extensions  the `ExtensionRegistry` containing any test extensions
     */
    private void cleanUp(ExceptionCollector     collector,
                         EngineExecutionContext context,
                         ExtensionRegistry      extensions) {

        @Inject TestResourceProvider provider;
        provider.setExecutionContext(context);
        collector.executeVoid(() -> lifecycle.cleanUp(collector, context, extensions));
    }

    /**
     * Report completion of test execution.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param extensions  the `ExtensionRegistry` containing any test extensions
     * @param skipResult  an optional `SkipResult` if a test was skipped
     * @param started     a flag to indicate whether test execution was started
     * @param duration    the duration the test took to execute
     */
    private Result reportCompletion(ExceptionCollector     collector,
                                    EngineExecutionContext context,
                                    ExtensionRegistry      extensions,
                                    SkipResult             skipResult,
                                    Boolean                started,
                                    Duration               duration) {

        @Inject TestResourceProvider provider;
        provider.setExecutionContext(context);

        Result result;
        if (collector.empty && skipResult.skipped) {
            result = collector.result.withDuration(duration);
            try {
                lifecycle.onSkipped(context, extensions, skipResult);
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
                lifecycle.onCompleted(context, extensions, result);
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
}