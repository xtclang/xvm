import xunit.MethodOrFunction;
import xunit.ResourceRegistry;

/**
 * Information about the current phase of execution of a test fixture.
 * A test fixture could be a test method, or a test container.
 */
class EngineExecutionContext
        implements xunit.ExecutionContext {
    /**
     * A private constructor to create a `EngineExecutionContext`
     * from a `Builder`.
     *
     * @param builder  the `Builder` to create the `EngineExecutionContext` from
     */
    private construct (Builder builder) {
        this.model          = builder.model;
        this.displayName    = builder.displayName;
        this.testModule     = builder.testModule;
        this.testPackage    = builder.testPackage;
        this.testClass      = builder.testClass;
        this.testMethod     = builder.testMethod;
        this.testFixture    = builder.testFixture;
        this.exception      = builder.exception;
        this.registry       = builder.registry;
        this.methodExecutor = builder.methodExecutor;
        this.listener       = builder.listener;
    }

    /**
     * The `Model` to execute.
     */
    Model model;

    /**
     * The `UniqueId` of the current test fixture.
     */
    UniqueId uniqueId.get() {
        return model.uniqueId;
     }

    /**
     * The human readable name for the test.
     */
    @Override
    String displayName;

    /**
     * The `Module` associated to the current test fixture.
     */
    @Override
    Module? testModule;

    /**
     * The `Package` associated to the current test fixture.
     */
    @Override
    Package? testPackage;

    /**
     * The `Class` associated to the current test fixture.
     */
    @Override
    Class? testClass;

    /**
     * The current test method.
     */
    @Override
    MethodOrFunction? testMethod;

    /**
     * The current test fixture the test method will execute against.
     */
    @Override
    Object? testFixture;

    /**
     * Any `Exception thrown during execution of the test lifecycle.
     */
    @Override
    Exception? exception;

    /**
     * The `ResourceRegistry` containing resources registered for this execution.
     */
    @Override
    ResourceRegistry registry;

    /**
     * The `MethodExecutor` to use to execute tests.
     */
    MethodExecutor methodExecutor;

    /**
     * The test `ExecutionListener`.
     */
    ExecutionListener listener;

    /**
     * Create a `Builder` from the specified `Model`.
     *
     * @param model  the `Model` to execute
     */
    static Builder builder(Model model) {
        return new Builder(model);
    }

    /**
     * Create a `EngineExecutionContext` from the specified `Model`.
     *
     * @param model  the `Model` to execute
     */
    static EngineExecutionContext create(Model model) {
        return builder(model).build();
    }

    /**
     * Create a `Builder` from this context and the specified `Model`.
     *
     * @param model  the `Model` to execute
     */
    Builder asBuilder(Model model) {
        return new Builder(this, model);
     }

    /**
     * Invoke a `MethodOrFunction` using any registered `ParameterResolver` resources
     * to resolve parameters for the function.
     *
     * @param method the `MethodOrFunction` to invoke
     *
     * @return the result of invoking the function
     */
    @Override
    Tuple invoke(MethodOrFunction method) {
        if (method.is(Method)) {
            assert testFixture != Null;
            return methodExecutor.invoke(method.as(Method), testFixture, this);
         }
        return methodExecutor.invoke(method.as(Function), this);
     }

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
    conditional Object invokeSingleResult(MethodOrFunction method) {
        Tuple tuple = invoke(method);
        if (tuple.size > 0) {
            return True, tuple[0];
         }
        return False;
    }

    /**
     * A `Builder` to build a `EngineExecutionContext`.
     */
    static class Builder {
        /**
         * Create a `Builder`.
         *
         * @param model  the `Model` to execute
         */
        private construct (Model model) {
            this.model          = model;
            this.displayName    = model.displayName;
            this.listener       = ExecutionListener.NoOp;
            this.registry       = new ResourceRegistry();
            this.methodExecutor = new MethodExecutor();
         }

        /**
         * Create a `Builder` using a parent `EngineExecutionContext`.
         *
         * @param ctx    the parent `EngineExecutionContext`
         * @param model  the `Model` to execute
         */
        private construct (EngineExecutionContext ctx, Model model) {
            this.model          = model;
            this.displayName    = model.displayName;
            this.testModule     = ctx.testModule;
            this.testPackage    = ctx.testPackage;
            this.testClass      = ctx.testClass;
            this.testMethod     = ctx.testMethod;
            this.testFixture    = ctx.testFixture;
            this.exception      = ctx.exception;
            this.methodExecutor = ctx.methodExecutor;
            this.listener       = ctx.listener;
            this.registry       = new ResourceRegistry(ctx.registry);
         }

        /**
         * The model to execute.
         */
        private Model model;

        /**
         * The human readable name for the test.
         */
        private String displayName;

        /**
         * The `Module` associated to the current test fixture.
         */
        private Module? testModule = Null;

        /**
         * The `Package` associated to the current test fixture.
         */
        private Package? testPackage = Null;

        /**
         * The `Class` associated to the current test fixture.
         */
        private Class? testClass = Null;

        /**
         * The current test method.
         */
        private MethodOrFunction? testMethod = Null;

        /**
         * The current test fixture the test method will execute against.
         */
        private Object? testFixture = Null;

        /**
         * Any `Exception`s thrown during execution of the test lifecycle.
         */
        private Exception? exception = Null;

        /**
         * The `ResourceRegistry` containing resources registered for this execution.
         */
        public/private ResourceRegistry registry;

        /**
         * The `MethodExecutor` to use to execute tests.
         */
        private MethodExecutor methodExecutor;

        /**
         * The `ExecutionListener`.
         */
        private ExecutionListener listener;

        Builder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        Builder withTestPackage(Package? testPackage) {
            this.testPackage = testPackage;
            return this;
        }

        Builder withTestClass(Class? testClass) {
            this.testClass = testClass;
            return this;
        }

        Builder withTestMethod(MethodOrFunction? testMethod) {
            this.testMethod = testMethod;
            return this;
        }

        Builder withTestFixture(Object? testFixture) {
            this.testFixture = testFixture;
            return this;
        }

        Builder withException(Exception? exception) {
            this.exception = exception;
            return this;
        }

        Builder withListener(ExecutionListener listener) {
            this.listener = listener;
            return this;
        }

        EngineExecutionContext build() {
            return new EngineExecutionContext(this);
        }
    }
}
