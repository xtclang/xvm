import extensions.ExtensionRegistry;

import xunit.MethodOrFunction;

/**
 * A base class for `ExecutionLifecycle` implementations.
 */
@Abstract const BaseExecutionLifecycle<ModelType extends Model>(ModelType model)
        implements ExecutionLifecycle {

    // ----- ExecutionLifecycle methods ------------------------------------------------------------

	@Override
    Iterable<Model> getChildren(EngineExecutionContext context) = model.children;

    // ----- helper methods ------------------------------------------------------------------------

	/**
	 * Returns the fixture to execute tests against, creating a new instance if required.
	 *
	 * @param context     the current `EngineExecutionContext`
	 * @param extensions  any test extensions
	 * @param clz         the test fixture class
	 *
	 * @return the fixture instance, or `Null` if this model is not able to
	 *         create a fixture instance
	 */
	Object? ensureFixture(EngineExecutionContext context, ExtensionRegistry extensions, Class clz) {
	    if (Object fixture := clz.isSingleton()) {
	        // ToDo: call pre/post fixture constructor extensions
	        return fixture;
        }

	    // ToDo: check extensions for any test fixture factory and use it if found

	    if (MethodOrFunction constructor := model.constructor.is(MethodOrFunction)) {
	        // ToDo: call pre/post fixture constructor extensions
	        if (Object fixture := context.invokeSingleResult(constructor)) {
	            return fixture;
            }
        }
		return Null;
    }
}