/**
 * A holder of information that an `ExtensionProvider` can use to provide
 * an `Extension`.
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
     * Invoke a `MethodOrFunction` using any registered `ParameterResolver` resources
     * to resolve parameters for the function.
     *
     * @param method the `MethodOrFunction` to invoke
     *
     * @return the result of invoking the function
     */
    Tuple invoke(MethodOrFunction method);
}