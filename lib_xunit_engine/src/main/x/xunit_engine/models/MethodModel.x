import xunit.SkipResult;
import xunit.MethodOrFunction;

import xunit.extensions.ExtensionProvider;
import xunit.extensions.ExtensionProviderProvider;

import xunit.templates.TestTemplate;

/**
 * A model that represents a method.
 *
 * This may be a real test method or function or a template test on top of a template model.
 */
interface MethodModel
        extends Model {

    /**
     * The test method's parent `Class`.
     */
    @RO Class testClass;

    /**
     * The test method or function this model represents.
     */
    @RO MethodOrFunction testMethod;

    /**
     * The `SkipResult` indicating whether the method is skipped.
     *
     * We still produce models for skipped test fixtures so that they will
     * be reported in any execution results.
     */
    @RO SkipResult skipResult;

    /**
     * Return a ModelBuilder for the test method or function.
     *
     * @param testClass   the test Class to build a model for
     * @param testMethod  the test method or function to build a model for
     *
     * @return a ModelBuilder for the test method or function
     */
    static ModelBuilder builder(Class testClass, MethodOrFunction testMethod) {
        return new Builder(testClass, testMethod);
    }

    // ----- inner service: Builder ----------------------------------------------------------------

    /**
     * A builder to build a `MethodModel`.
     *
     * The exact type of model built will depend on the type of the underlying test method or
     * function.
     */
    static service Builder
            implements ModelBuilder {

        construct(Class clz, MethodOrFunction testMethod) {
            this.uniqueId    = UniqueId.forObject(testMethod);
            this.clz         = clz;
            this.testMethod  = testMethod;
            this.isContainer = testMethod.is(TestTemplate);
        }

        @Override
        public/private UniqueId uniqueId;

        @Override
        public/private Boolean isContainer;

        /**
         * The test `Class`.
         */
        public/private Class clz;

        /**
         * The test `Method` or `Function`.
         */
        public/private MethodOrFunction testMethod;

        @Override
        Model build(DiscoveryConfiguration configuration, Model[] children) {
            String              name        = configuration.displayNameFor(clz, testMethod);
            Type                type        = clz.toType();
            Model.Constructor?  constructor = clz.isSingleton() ? Null : utils.findTestConstructor(type);
            ExtensionProvider[] providers   = new Array();

            if (testMethod.is(ExtensionProviderProvider)) {
                providers.addAll(testMethod.as(ExtensionProviderProvider).getExtensionProviders());
            }

            MethodModel model = new SimpleMethodModel(uniqueId, clz, testMethod, name,
                                                      constructor, providers);

            if (testMethod.is(TestTemplate)) {
                model = new TemplatedMethodModel(model, children);
            }
            return model;
        }

        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        Int estimateStringLength() = uniqueId.estimateStringLength() + clz.estimateStringLength();

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            "MethodModelBuilder(".appendTo(buf);
            uniqueId.appendTo(buf);
            ")".appendTo(buf);
            return buf;
        }
    }
}
