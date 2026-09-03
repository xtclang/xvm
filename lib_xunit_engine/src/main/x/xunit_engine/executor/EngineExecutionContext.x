import ecstasy.annotations.Inject.Options;

import xunit.MethodOrFunction;

import xunit.extensions.ExecutionContext;

import xunit.extensions.MethodExecutor;

/**
 * Information about the current phase of execution of a test fixture.
 * A test fixture could be a test method, or a test container.
 */
@Concurrent
service EngineExecutionContext
        implements ExecutionContext {
    /**
     * A private constructor to create a `EngineExecutionContext`
     * from a `Builder`.
     *
     * @param builder  the `Builder` to create the `EngineExecutionContext` from
     */
    private construct (Builder builder) {
        this.model          = builder.model;
        this.displayName    = builder.displayName;
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
    public/private Model model;

    /**
     * The `UniqueId` of the current test fixture.
     */
    @Override
    UniqueId uniqueId.get() = model.uniqueId;

    /**
     * The human readable name for the test.
     */
    @Override
    public/private String displayName;

    /**
     * The `Class` associated to the current test fixture.
     */
    @Override
    public/private Class? testClass;

    /**
     * The current test method.
     */
    @Override
    public/private MethodOrFunction? testMethod;

    /**
     * The current test fixture the test method will execute against.
     */
    @Override
    public/private Ref<Object>? testFixture;

    /**
     * Any `Exception thrown during execution of the test lifecycle.
     */
    @Override
    public/private Exception? exception;

    /**
     * The `ResourceRegistry` containing resources registered for this execution.
     */
    @Override
    public/private ResourceRegistry registry;

    /**
     * The `MethodExecutor` to use to execute tests.
     */
    @Override
    public/private MethodExecutor methodExecutor;

    /**
     * The test `ExecutionListener`.
     */
    public/private ExecutionListener listener;

    /**
     * The current execution results.
     */
    public/private Result results = new Result(Successful, count=0);

    /**
     * Create a `Builder` from the specified `Model`.
     *
     * @param model  the `Model` to execute
     */
    static Builder builder(Model model) = new Builder(model);

    /**
     * Create a `EngineExecutionContext` from the specified `Model`.
     *
     * @param model  the `Model` to execute
     */
    static EngineExecutionContext create(Model model) = builder(model).build();

    /**
     * Create a `Builder` from this context and the specified `Model`.
     *
     * @param model  the `Model` to execute
     */
    Builder asBuilder(Model model) = new Builder(this, model);

    @Override
    conditional Object lookup(Type type, String name, Options opts = Null) {
        switch (type, name) {
            case (Class?, "testClass"):
                return True, testClass;

            case (MethodOrFunction?, "testMethod"):
                return True, testMethod;

            case (String, "displayName"):
                return True, displayName;
        }

        Type requiredType = type;
        if ((Type left, Type right) := type.relational()) {
            requiredType = left.is(Type<Nullable>) ? right : left;
        }

        // try in the resource registry
        if (Object o := registry.get(requiredType, name)) {
            return True, o;
        }
        // try in the ResourceRegistry without a name
        if (Object o := registry.get(requiredType)) {
            return True, o;
        }
        return False;
    }

    void onCompleted(Result result) {
        this.results = this.results.merge(result);
    }

    /**
     * A `Builder` to build a `EngineExecutionContext`.
     */
    static service Builder {
        /**
         * Create a `Builder`.
         *
         * @param model  the `Model` to execute
         */
        private construct (Model model) {
            this.model          = model;
            this.displayName    = model.displayName;
            this.listener       = ExecutionListener.NoOp;
            this.registry       = new SimpleResourceRegistry();
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
            this.testClass      = ctx.testClass;
            this.testMethod     = ctx.testMethod;
            this.testFixture    = ctx.testFixture;
            this.exception      = ctx.exception;
            this.methodExecutor = ctx.methodExecutor;
            this.listener       = ctx.listener;
            this.registry       = ctx.registry.copy();
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
        private Ref<Object>? testFixture = Null;

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

        Builder withTestFixture(Ref<Object> testFixture) {
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

        EngineExecutionContext build() = new EngineExecutionContext(this);
    }
}
