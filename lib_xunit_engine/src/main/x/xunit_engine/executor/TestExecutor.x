import extensions.ExtensionRegistry;

import xunit.Extension;
import xunit.ExtensionProvider;
import xunit.SkipResult;

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
     * @param context    the `EngineExecutionContext` to use
     * @param providers  the  `ExtensionProvider`s to use
     */
    EngineExecutionContext execute(EngineExecutionContext context, ExtensionProvider[] providers) {
        ExtensionRegistry? registry = Null;
        if (providers.size > 0) {
            registry = new ExtensionRegistry();
    	    for (ExtensionProvider ep : providers) {
    	        for (Extension extension : ep.getExtensions(context)) {
        	        registry.add(extension, ep);
                }
            }
        }
        return execute(context, registry);
    }

    /**
     * Execute the tests in this executor's `Model` including recursively
     * executing tests in any child `Model's.
     *
     * @param parentContext  the parent `EngineExecutionContext` to use
     *
     */
    EngineExecutionContext execute(EngineExecutionContext parentContext, ExtensionRegistry? parentExtensions = Null) {
        @Inject Clock          clock;
        ExtensionRegistry      extensions = new ExtensionRegistry();
        ExceptionCollector     collector  = new ExceptionCollector();
        SkipResult             skipResult = NotSkipped;
        Boolean                started    = False;
        EngineExecutionContext context    = parentContext;
        Time                   start      = clock.now;
        Duration               duration   = None;

		if (context := collector.execute(() -> lifecycle.prepare(parentContext, extensions, parentExtensions))) {
            if (skipResult := collector.execute(() -> lifecycle.shouldBeSkipped(context, extensions))) {
                if (collector.empty && !skipResult.skipped) {
                    if (collector.executeVoid(() -> context.listener.onStarted(model))) {
                        started = True;
                        if (context := executeRecursively(collector, context, extensions)) {
                            cleanUp(collector, context, extensions);
                            duration = clock.now - start;
                        }
                    }
                }
            }
        }
        reportCompletion(collector, context, extensions, skipResult, started, duration);
        return context;
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
            context = lifecycle.before(collector, context, extensions);
            if (collector.empty) {
                context = lifecycle.execute(collector, context, extensions);
                lifecycle.getChildren(context)
                        .map(child -> new TestExecutor(child, configuration))
                        .forEach(executor -> executor.execute(context, extensions));
                collector.executeVoid(() -> lifecycle.after(collector, context, extensions));
            }
            return context;
        });
    }

    /**
     * Clean up the test execution.
     *
     * @param collector   the `ExceptionCollector` to collect any exceptions
     * @param context     the current `EngineExecutionContext`
     * @param extensions  the `ExtensionRegistry` containing any test extensions
     */
    private void cleanUp(ExceptionCollector collector, EngineExecutionContext context, ExtensionRegistry extensions) {
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
    private void reportCompletion(ExceptionCollector collector, EngineExecutionContext context,
            ExtensionRegistry extensions, SkipResult skipResult, Boolean started, Duration duration) {

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
    }
}