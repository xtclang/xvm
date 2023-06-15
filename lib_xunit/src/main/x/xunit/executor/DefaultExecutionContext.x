import  registry.ResourceRegistry;

/**
 * A default implementation of an `ExecutionContext`.
 */
class DefaultExecutionContext
        implements ExecutionContext {

    private construct (Builder builder) {
        this.model          = builder.model;
//        this.listener       = builder.listener;
        this.displayName    = builder.displayName;
        this.testModule     = builder.testModule;
        this.testPackage    = builder.testPackage;
        this.testClass      = builder.testClass;
        this.testMethod     = builder.testMethod;
        this.testFixture    = builder.testFixture;
        this.exception      = builder.exception;
        this.registry     = builder.registry;
        this.methodExecutor = builder.methodExecutor;
    }

    Model model;

//    ExecutionListener listener;

    @Override
    UniqueId uniqueId.get() {
        return model.uniqueId;
     }

    @Override
    String displayName;

    @Override
    Module? testModule;

    @Override
    Package? testPackage;

    @Override
    Class? testClass;

    @Override
    TestMethodOrFunction? testMethod;

    @Override
    Object? testFixture;

    @Override
    Exception? exception;

    @Override
    ResourceRegistry registry;

    @Override
    MethodExecutor methodExecutor;

    Builder asBuilder(Model model) {
        return new Builder(this, model);
     }

    static Builder builder(Model model) {
        return new Builder(model);
     }

    static DefaultExecutionContext create(Model model) {
        return builder(model).build();
    }

    // ---- inner class: Builder -------------------------------------------------------------------

    static class Builder {
        construct (Model model) {
            this.model          = model;
            this.displayName    = model.displayName;
            this.registry       = new ResourceRegistry();
            this.methodExecutor = new DefaultMethodExecutor();
         }

        construct (DefaultExecutionContext ctx, Model model) {
            this.model  = model;
            this.displayName    = model.displayName;
//            this.listener       = ctx.listener;
            this.testModule     = ctx.testModule;
            this.testPackage    = ctx.testPackage;
            this.testClass      = ctx.testClass;
            this.testMethod     = ctx.testMethod;
            this.testFixture    = ctx.testFixture;
            this.exception      = ctx.exception;
            this.methodExecutor = ctx.methodExecutor;
            this.registry       = new ResourceRegistry(ctx.registry);
         }

        private Model model;
        
//        private ExecutionListener listener = ExecutionListener.NoOp;

        private String displayName;

        private Module? testModule = Null;

        private Package? testPackage = Null;

        private Class? testClass = Null;

        private TestMethodOrFunction? testMethod = Null;

        private Object? testFixture = Null;

        private Exception? exception = Null;

        public/private ResourceRegistry registry;

        private MethodExecutor methodExecutor;

//        Builder withListener(ExecutionListener listener) {
//            this.listener = listener;
//            return this;
//         }

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

        Builder withTestMethod(TestMethodOrFunction? testMethod) {
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

        DefaultExecutionContext build() {
            return new DefaultExecutionContext(this);
         }
     }
}