import xunit.annotations.TestEngineExtender;

import xunit.extensions.Extension;

/**
 * The configuration to control running a test suite.
 */
const TestConfiguration {
    /**
     * Create an `TestConfiguration`.
     *
     * @param builder  the `Builder` to create the config from
     */
    construct(Builder builder) {
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
    Extension[] extensions;

    /**
     * Return this `TestConfiguration` as a `Builder`.
     */
    Builder asBuilder() = new Builder(this);

    /**
     * Create an `TestConfiguration` builder.
     */
    static Builder builder() = new Builder();

    /**
     * An `TestConfiguration` builder.
     */
    static const Builder {
        /**
         * Create a `Builder`.
         */
        construct(DiscoveryConfiguration? discoveryConfig = Null,
                  ExecutionConfiguration? executionConfig = Null,
                  Extension[]             extensions      = []) {

        this.discoveryConfig = discoveryConfig ?: DiscoveryConfiguration.builder().build();
        this.executionConfig = executionConfig ?: ExecutionConfiguration.builder().build();
        this.extensions      = extensions;
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
        Extension[] extensions;

        /**
         * Create a `Builder`.
         *
         * @param config  the `TestConfiguration` to use to create the builder
         */
        construct(TestConfiguration config) {
            this.discoveryConfig = config.discoveryConfig;
            this.executionConfig = config.executionConfig;
            this.extensions      = config.extensions;
        }

        /**
         * Return a new builder that is a copy of this builder with the specified
         * `DiscoveryConfiguration` to use to discover tests.
         *
         * @param config  the `DiscoveryConfiguration`
         *
         * @return a new builder that is a copy of this builder with the specified
         *         `DiscoveryConfiguration` to use to discover tests.
         */
        Builder withDiscoveryConfig(DiscoveryConfiguration config)
                = new Builder(config, this.executionConfig, this.extensions);

        /**
         * Return a new builder that is a copy of this builder with the specified
         * `ExecutionConfiguration` to use to execute tests.
         *
         * @param config  the `ExecutionConfiguration`
         *
         * @return a new builder that is a copy of this builder with the specified
         *         `ExecutionConfiguration` to use to execute tests
         */
        Builder withExecutionConfig(ExecutionConfiguration config)
                = new Builder(this.discoveryConfig, config, this.extensions);

        /**
         * Return a new builder that is a copy of this builder adding the specified `Extension`.
         *
         * @param extension  the `Extension` to add
         *
         * @return a new builder that is a copy of this builder adding the specified `Extension`
         */
        Builder withExtension(Extension extension)
                = new Builder(this.discoveryConfig, this.executionConfig,
                              this.extensions.add(extension));

        /**
         * Return a new builder that is a copy of this builder adding the specified `Extension`s.
         *
         * @param extensions  the `Extension`s to add
         *
         * @return a new builder that is a copy of this builder adding the specified `Extension`s
         */
        Builder withExtensions(Extension[] extensions)
                = new Builder(this.discoveryConfig, this.executionConfig,
                              this.extensions.addAll(extensions));

        /**
         * Return a new builder that is a copy of this builder with the addition of any engine
         * extensions that test module or any of its dependencies provides.
         *
         * @param testModule  the test module
         *
         * @return a new builder that is a copy of this builder with the addition of any engine
         *         extensions that test module or any of its dependencies provides
         */
        Builder discoverExtensions(Module testModule) {
            Extension[]              extensions   = new Array();
            Map<String, Extension[]> extensionMap = new HashMap();
            discoverExtensions(testModule, extensionMap);
            for (Extension[] extensionsForModule : extensionMap.values) {
                extensions.addAll(extensionsForModule);
            }
            return withExtensions(extensions);
        }

        private void discoverExtensions(Module testModule, Map<String, Extension[]> extensions) {
            if (extensions.contains(testModule.qualifiedName)) {
                return;
            }

            Class clz  = &testModule.class;
            Type  type = &testModule.type;
            if (clz.name == TypeSystem.MackPackage) {
                return;
            }

            if (testModule.is(TestEngineExtender)) {
                Extension[] moduleExtensions = testModule.getTestEngineExtensions();
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
