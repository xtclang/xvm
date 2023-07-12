import executor.ExecutionLifecycle;
import executor.MethodExecutionLifecycle;

import extensions.ExtensionRegistry;

import xunit.ExtensionProvider;
import xunit.SkipResult;
import xunit.TestExecutionPredicate;
import xunit.MethodOrFunction;

import xunit.annotations.Disabled;


/**
 * A `Model` representing a test method.
 */
const MethodModel
        extends BaseModel {
    /**
     * Create a `MethodModel`.
     *
     * @param id           a unique id for this test method
     * @param testClass    the test method's parent `Class`
     * @param testMethod   the test `Method` or `Function`
     * @param displayName  the name for this model to use when displaying test information
     * @param constructor  an optional constructor to use to create the test fixture
     * @param providers    the `ExtensionProvider`s this model will use to add additional extensions
     *                     to the test execution
     */
    construct (UniqueId id, Class testClass, MethodOrFunction testMethod, String displayName, Constructor? constructor) {
        this.testClass  = testClass;
        this.testMethod = testMethod;

        if (testClass.is(Disabled)) {
            this.skipResult = new SkipResult(True, testClass.reason);
        }
        else if (testMethod.is(Disabled)) {
            this.skipResult = new SkipResult(True, testMethod.reason);
        }
        else {
            this.skipResult = SkipResult.NotSkipped;
        }
        construct BaseModel(id, displayName, False, constructor);
    }

    /**
     * The test method's parent `Class`.
     */
    public/private Class testClass;

    /**
     * The test method or function this model represents.
     */
    public/private MethodOrFunction testMethod;

    /**
     * The `SkipResult` indicating whether the method is skipped.
     *
     * We still produce models for skipped test fixtures so that they will
     * be reported in any execution results.
     */
    public/private SkipResult skipResult;

    /**
     * The lazily calculated string value for this model.
     */
    @Lazy private String stringValue.calc() {
        return $"MethodModel(id={uniqueId})";
    }

    @Override
    ExecutionLifecycle createExecutionLifecycle() {
        return new MethodExecutionLifecycle(this);
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return stringValue.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return stringValue.appendTo(buf);
    }

    // ----- inner service: Builder ----------------------------------------------------------------

    static service Builder
            implements ModelBuilder<MethodModel>
            implements Stringable {

        construct(Class clz, MethodOrFunction testMethod) {
            this.uniqueId   = UniqueId.forObject(testMethod);
            this.clz        = clz;
            this.testMethod = testMethod;
        }

        @Override
        public/private UniqueId uniqueId;

        /**
         * The test `Class`.
         */
        public/private Class clz;

        /**
         * The test `Method` or `Function`.
         */
        public/private MethodOrFunction testMethod;

        @Override
        MethodModel build(DiscoveryConfiguration configuration, Model[] children) {
            String              name        = configuration.displayNameFor(clz, testMethod);
            Type                type        = clz.toType();
            Model.Constructor?  constructor = clz.isSingleton() ? Null : utils.findTestConstructor(type);

            return new MethodModel(uniqueId, clz, testMethod, name, constructor);
        }

        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        Int estimateStringLength() {
            return uniqueId.estimateStringLength() + clz.estimateStringLength();
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            "MethodModelBuilder(".appendTo(buf);
            uniqueId.appendTo(buf);
            ")".appendTo(buf);
            return buf;
        }
    }
}
