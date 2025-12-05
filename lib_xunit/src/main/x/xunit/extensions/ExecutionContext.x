import ecstasy.annotations.Inject.Options;

/**
 * A holder of test execution information.
 */
interface ExecutionContext {
    /**
     * The human readable name for the test.
     */
    @RO String displayName;

    /**
     * The `Module` associated to the current test fixture.
     */
    @RO Module? testModule;

    /**
     * The `Package` associated to the current test fixture.
     */
    @RO Package? testPackage;

    /**
     * The `Class` associated to the current test fixture.
     */
    @RO Class? testClass;

    /**
     * The current test method.
     */
    @RO MethodOrFunction? testMethod;

    /**
     * The `ResourceRegistry` containing resources registered for this execution.
     */
    @RO ResourceRegistry registry;

    /**
     * The current test fixture the test method will execute against.
     */
    @RO Object? testFixture;

    /**
     * Any `Exception thrown during execution of the test lifecycle.
     */
    @RO Exception? exception;

    /**
     * Invoke a `MethodOrFunction` using any registered `ParameterResolver` resources to resolve
     * parameters for the function.
     *
     * @param method the `MethodOrFunction` to invoke
     *
     * @return the result of invoking the function
     */
    Tuple invoke(MethodOrFunction method);

    /**
     * Invoke a `MethodOrFunction` using any registered `ParameterResolver` resources
     * to resolve parameters for the function and return the single result returned by the
     * invocation.
     *
     * @param method the `MethodOrFunction` to invoke
     *
     * @return `True` iff the invocation returned a result
     * @return the single result of invoking the function
     */
    conditional Object invokeSingleResult(MethodOrFunction method);

    /**
     * Lookup a resource stored in this context.
     *
     * @param type  the type of the resource to lookup
     * @param name  the name of the resource to lookup
     * @param opts  the options to use when looking up the resource
     *
     * @return True iff this context contains the requested resource
     * @return the requested resource
     */
    conditional Object lookup(Type type, String name, Options opts = Null);

}