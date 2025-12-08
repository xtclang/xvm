import extensions.EngineExtension;
import extensions.TestEngineExtender;

/**
 * The configuration to control running a test suite.
 */
const TestConfiguration {
    /**
     * Create an `TestConfiguration`.
     *
     * @param builder  the `Builder` to create the config from
     */
    private construct(Builder builder) {
        this.discoveryConfig = builder.discoveryConfig;
        this.executionConfig = builder.executionConfig;
        this.extensions      = builder.extensions;
    }

    /**
     * The configuration to control discovery of tests.
     */
    DiscoveryConfiguration discoveryConfig;

    /**
     * The configuration to control execution of tests.
     */
    ExecutionConfiguration executionConfig;

    /**
     * The engine extensions.
     */
    EngineExtension[] extensions;

    /**
     * Return this `TestConfiguration` as a `Builder`.
     */
    Builder asBuilder() = new Builder(this);

    /**
     * Create a default `TestConfiguration`.
     */
    static TestConfiguration create() = builder().build();

    /**
     * Create an `TestConfiguration` builder.
     */
    static Builder builder() = new Builder();

    /**
     * An `TestConfiguration` builder.
     */
    static class Builder {
        /**
         * Create a `Builder`.
         */
        construct() {
        }

        /**
         * The configuration to control discovery of tests.
         */
        private DiscoveryConfiguration discoveryConfig = DiscoveryConfiguration.create();

        /**
         * The configuration to control execution of tests.
         */
        private ExecutionConfiguration executionConfig = ExecutionConfiguration.create();

        /**
         * The engine extensions.
         */
        private EngineExtension[] extensions = new Array();

        /**
         * Create a `Builder`.
         *
         * @param config  the `TestConfiguration` to use to create the builder
         */
        construct(TestConfiguration config) {
            this.discoveryConfig = config.discoveryConfig;
            this.executionConfig = config.executionConfig;
        }

        /**
         * Set the `DiscoveryConfiguration` to use to discover tests.
         *
         * @param config  the `DiscoveryConfiguration`
         */
        Builder withDiscoveryConfig(DiscoveryConfiguration config) {
            this.discoveryConfig = config;
            return this;
        }

        /**
         * Set the `ExecutionConfiguration` to use to execute tests.
         *
         * @param config  the `ExecutionConfiguration`
         */
        Builder withExecutionConfig(ExecutionConfiguration config) {
            this.executionConfig = config;
            return this;
        }

        /**
         * Add any engine extensions that test module or any of its dependencies provides.
         *
         * @param testModule  the test module
         */
        Builder discoverExtensions(Module testModule) {
            Map<String, EngineExtension[]> extensionMap = new HashMap();
            discoverExtensions(testModule, extensionMap);
            for (EngineExtension[] extensions : extensionMap.values) {
                this.extensions.addAll(extensions);
            }
            return this;
        }

        private void discoverExtensions(Module testModule, Map<String, EngineExtension[]> extensions) {
            if (extensions.contains(testModule.qualifiedName)) {
                return;
            }

            Class clz  = &testModule.class;
            Type  type = &testModule.type;
            if (clz.name == TypeSystem.MackPackage) {
                return;
            }

            if (testModule.is(TestEngineExtender)) {
                EngineExtension[] moduleExtensions = testModule.getTestEngineExtensions();
                extensions.put(testModule.qualifiedName, moduleExtensions);
            } else {
                extensions.put(testModule.qualifiedName, []);
            }

            for (Type child : type.childTypes.values) {
                if (child.is(Type<Package>)) {
                    assert Class childClass := child.fromClass();
                    if (Object o := childClass.isSingleton()) {
                        Package pkg = o.as(Package);
                        if (Module childModule := pkg.isModuleImport()) {
                            discoverExtensions(childModule, extensions);
                        }
                    }
                }
            }
        }

        /**
         * Build the `TestConfiguration`.
         *
         * @return an `TestConfiguration` built from this `Builder`
         */
        TestConfiguration build() = new TestConfiguration(this);
    }
}
