import ecstasy.annotations.Inject.Options;

/**
 * A holder of test execution information.
 */
interface ExecutionContext {
    /**
     * The `UniqueId` of the current test fixture.
     */
    @RO UniqueId uniqueId;

    /**
     * The human readable name for the test.
     */
    @RO String displayName;

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
     * A reference to the current test fixture the test method will execute against.
     */
    @RO Ref<Object>? testFixture;

    /**
     * Any `Exception thrown during execution of the test lifecycle.
     */
    @RO Exception? exception;

    /**
     * The `MethodExecutor` to use to execute methods and functions.
     */
    @RO MethodExecutor methodExecutor;

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
