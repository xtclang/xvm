import extensions.ExtensionRegistry;

import xunit.SkipResult;

/**
 * An `ExecutionLifecycle` is the callback methods implemented by a test or container to handle
 * various phases of a test lifecycle.
 */
interface ExecutionLifecycle
        extends Const
    {
    /**
     * Return the child `Model`s.
     *
     * @return the child `Model`s
     */
    Iterable<Model> getChildren(EngineExecutionContext context) = [];

	/**
	 * Prepare an EngineExecutionContext prior to execution.
	 *
	 * @param context     the optional parent `EngineExecutionContext
	 * @param extensions  the extensions specific to the current model
	 */
	EngineExecutionContext prepare(EngineExecutionContext context, ExtensionRegistry extensions)
	        = context;

	/**
	 * Determine whether the execution of the supplied EngineExecutionContext should be skipped.
	 *
	 * @param context     the `EngineExecutionContext` to execute in
	 * @param extensions  the extensions specific to the current model
	 */
	SkipResult shouldBeSkipped(EngineExecutionContext context, ExtensionRegistry extensions)
		    = SkipResult.NotSkipped;

	/**
	 * Execute the before behavior of this ExecutionLifecycle.
	 *
	 * This method will be called once before execution of this ExecutionLifecycle.
	 *
	 * @param collector   the exception collector to use
	 * @param context     the `EngineExecutionContext` to execute in
	 * @param extensions  the extensions specific to the current model
	 *
	 * @return the context to use to execute children of this ExecutionLifecycle
	 */
	EngineExecutionContext before(ExceptionCollector     collector,
                                  EngineExecutionContext context,
                                  ExtensionRegistry      extensions) = context;

	/**
	 * Execute the behavior of this ExecutionLifecycle.
	 *
	 * Test containers (i.e. classes, modules or packages) would not typically implement this method
	 * as the `TestEngine` will handle execution of their child ExecutionLifecycles.
	 *
	 * @param collector   the exception collector to use
	 * @param context     the `EngineExecutionContext` to execute in
	 * @param extensions  the extensions specific to the current model
	 *
	 * @return the context to use to execute children of this ExecutionLifecycle and execution
	 *         of the after behaviour in the parent of this ExecutionLifecycle
	 */
	EngineExecutionContext execute(ExceptionCollector     collector,
                                   EngineExecutionContext context,
                                   ExtensionRegistry      extensions) = context;

	/**
	 * Execute any after behavior for this ExecutionLifecycle. This method will be called once,
	 * after execution of this ExecutionLifecycle.
	 *
	 * @param collector   the exception collector to use
	 * @param context     the `EngineExecutionContext` to execute in
	 * @param extensions  the extensions specific to the current model
	 */
	void after(ExceptionCollector     collector,
	           EngineExecutionContext context,
	           ExtensionRegistry      extensions) {
    }

	/**
	 * Clean up the supplied EngineExecutionContext after execution.
	 *
	 * @param collector   the exception collector to use
	 * @param context     the `EngineExecutionContext` to execute in
	 * @param extensions  the extensions specific to the current model
	 */
	void cleanUp(ExceptionCollector     collector,
	             EngineExecutionContext context,
	             ExtensionRegistry      extensions) {
    }

	/**
	 * a callback that will be invoked if execution of this ExecutionLifecycle was skipped.
	 *
	 * @param context     the `EngineExecutionContext`
	 * @param extensions  the extensions specific to the current model
	 * @param result      the result of skipped execution
	 */
	void onSkipped(EngineExecutionContext context,
                   ExtensionRegistry      extensions,
                   SkipResult             result) {
    }

	/**
	 * a callback that will be invoked when execution of this ExecutionLifecycle has completed.
	 *
	 * @param context     the `EngineExecutionContext`
	 * @param extensions  the extensions specific to the current model
	 * @param result      the result of the execution
	 */
	void onCompleted(EngineExecutionContext context, ExtensionRegistry extensions, Result result)
	    {
	    }

    /**
     * A no-op instance of an `ExecutionLifecycle`.
     */
	static const NoOp
	        implements ExecutionLifecycle
        {
        /**
         * A singleton instance of NoOp.
         */
        static ExecutionLifecycle Instance = new NoOp();
        }
    }
