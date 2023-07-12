import executor.ContainerExecutionLifecycle;
import executor.ExecutionLifecycle;

import xunit.ExtensionProvider;


/**
 * A test model representing a container of other models.
 *
 * The type this model represents would typically be a container of
 * other models, such as a `Class`, or `Module` or `Package` containing
 * other containers or tests.
 */
const ContainerModel
        extends BaseModel {

    /**
     * Create a `ContainerModel`.
     *
     * @param id                  a unique id for this test fixture
     * @param clz                 the `Class` of the test fixture
     * @param displayName         the name for this model to use when displaying test information
     * @param constructor         the constructor to use to create the test fixture
     * @param extensionProviders  the `ExtensionProvider`s this model will add
     * @param children            the child models of this model
     */
    construct (UniqueId id, Class clz, String displayName, Constructor? constructor,
            ExtensionProvider[] extensionProviders, Model[] children) {
        this.testClass = clz;
        this.testType  = clz.toType();
        construct BaseModel(id, displayName, True, constructor, extensionProviders, children);
        }

    /**
     * The test fixture's `Type`.
     */
    public/private Class testClass;

    /**
     * The test fixture's `Type`.
     */
    public/private Type testType;

    /**
     * The lazily calculated string value for this model.
     */
    @Lazy private String stringValue.calc() {
        return $"ContainerModel(id={uniqueId})";
    }

    @Override
    ExecutionLifecycle createExecutionLifecycle() {
        return new ContainerExecutionLifecycle(this);
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

    /**
     * A `ModelBuilder` that builds a `ContainerModel`.
     */
    static service Builder
            implements ModelBuilder<ContainerModel> {

        /**
         * Create a `Builder` to build a `ContainerModel` for a `Class`.
         *
         * @param clz  the `Class` to build the `ContainerModel` for
         */
        construct(Class clz) {
            this.uniqueId = UniqueId.forClass(clz);
            this.clz      = clz;
        }

        @Override
        public/private UniqueId uniqueId;

        public/private Class clz;

        @Override
        ContainerModel build(DiscoveryConfiguration configuration, Model[] children) {
            String              name        = configuration.displayNameFor(clz);
            Type                type        = clz.toType();
            Model.Constructor?  constructor = clz.isSingleton() ? Null : utils.findTestConstructor(type);
            ExtensionProvider[] providers   = utils.findExtensions(clz);

            return new ContainerModel(uniqueId, clz, name, constructor, providers, children);
        }
    }
}
