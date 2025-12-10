import discovery.Selector;

import xunit.MethodOrFunction;

import xunit.extensions.DisplayNameGenerator;

/**
 * The configuration used to determine how test fixtures and tests are discovered
 * for a specific test execution.
 *
 * A test discovery configuration may be to discover just a single test method, or
 * discover all tests in a class, package or module, or all tests of a specific
 * test group, or a combination of any of these.
 */
const DiscoveryConfiguration {

    /**
     * Create a DiscoveryConfiguration from a `Builder`.
     */
    construct(Builder builder) {
        this.displayNameGenerator = builder.displayNameGenerator;
        this.selectors            = builder.selectors.freeze(False);
        this.verbose              = builder.verbose;
    }

    /**
     * The `Selector`s to use to discover tests.
     */
    Selector[] selectors;

    /**
     * The `DisplayNameGenerator` to use.
     */
    DisplayNameGenerator displayNameGenerator;

    /**
     * A flag indicating whether to enable verbose discovery logging.
     */
    Boolean verbose;

    /**
     * Return this `DiscoveryConfiguration` as a `Builder`.
     */
    Builder asBuilder() = new Builder(this);

    /**
     * A convenience method to return the display name for a
     * `Class` using the configured `DisplayNameGenerator`.
     *
     * @param clz  the `Class` to obtain the display name for
     *
     * @return the human readable display name for the `Class`
     */
    String displayNameFor(Class clz) = displayNameGenerator.nameForClass(clz);

    /**
     * A convenience method to return the display name for a
     * `Class` using the configured `DisplayNameGenerator`.
     *
     * @param clz     the `Class` to obtain the display name for
     * @param method  the test method or function to obtain the display name for
     *
     * @return the human readable display name for the `Class`
     */
    String displayNameFor(Class clz, MethodOrFunction method)
            = displayNameGenerator.nameForMethod(clz, method);

    // ----- factory methods -----------------------------------------------------------------------

    /**
     * Create a `DiscoveryConfiguration` builder.
     *
     * @param testModule  the `Module` containing the tests to discover, or `Null` to create a
     *                    default empty builder
     */
    static Builder builder(Module? testModule = Null) {
        if (testModule.is(Module)) {
            assert Selector selector := discovery.selectors.forModule(testModule);
            return new Builder([selector]);
        }
        return new Builder();
    }

    // ----- inner class: Builder ------------------------------------------------------------------

    /**
     * A `DiscoveryConfiguration` builder.
     *
     * @param selectors             the `Selector`s to use to discover tests
     * @param displayNameGenerator  the `DisplayNameGenerator` to use
     * @param verbose               a flag indicating whether to enable verbose discovery logging
     */
    static const Builder(Selector[]           selectors            = [],
                         DisplayNameGenerator displayNameGenerator = DisplayNameGenerator.Default,
                         Boolean              verbose              = False) {

        /**
         * Create a `Builder`.
         *
         * @param config  the `DiscoveryConfiguration` to use to create the builder
         */
        construct(DiscoveryConfiguration config) {
            this.displayNameGenerator = config.displayNameGenerator;
            this.verbose              = config.verbose;
            this.selectors            = new Array();
            this.selectors.addAll(config.selectors);
            this.selectors.freeze(True);
        }

        /**
         * Return a `Builder` that is a copy of this builder configured with default values.
         *
         * @param testModule  the `module containing the tests to discover
         */
        Builder autoConfigure(Module testModule) {
            @Inject(discovery.ConfigDiscoveryTestPackage) String[]? testPackages;
            @Inject(discovery.ConfigDiscoveryTestClass)   String[]? testClasses;
            @Inject(discovery.ConfigDiscoveryTest)        String[]? tests;
            @Inject(discovery.ConfigDiscoveryGroup)       String[]? testGroups;
            @Inject(discovery.ConfigDiscoveryVerbose)     String?   verboseConfig;

            Boolean verbose = verboseConfig.is(String) && verboseConfig.toLowercase() == "true";

            Selector[] selectors = new Array();
            if (testPackages.is(String[])) {
                for (String testPackage : testPackages) {
                    if (Class clz := findClass(testModule, testPackage)) {
                        assert:arg Selector selector := discovery.selectors.forPackage(clz)
                                as $"Invalid test package specified {testPackage}";
                        selectors.add(selector);
                    } else {
                        throw new IllegalArgument($"Invalid test package specified {testPackage}");
                    }
                }
            }

            if (testClasses.is(String[])) {
                for (String testClass : testClasses) {
                    if (Class clz := findClass(testModule, testClass)) {
                        assert:arg Selector selector := discovery.selectors.forClass(clz)
                                as $"Invalid test class specified {testClass}";
                        selectors.add(selector);
                    } else {
                        throw new IllegalArgument($"Invalid test class specified {testClass}");
                    }
                }
            }

            if (tests.is(String[])) {
                for (String test : tests) {
// ToDo JK: Fix why specifying a method seems to hang somewhere
//                    assert:arg Selector[] testSelectors := discovery.selectors.forMethod(clz, test)
//                            as $"Invalid test method specified {testClass}.{test}";
//                    selectors.add(selector);
                }
            }

            if (selectors.empty) {
                // no custom selectors were specified so default to the whole module
                assert Selector selector := discovery.selectors.forModule(testModule);
                selectors.add(selector);
            }

            return new Builder(selectors, this.displayNameGenerator, verbose=verbose);
        }

        private conditional Class findClass(Module testModule, String className) {
            TypeSystem typeSystem = &testModule.type.typeSystem;
            String     moduleName = testModule.simpleName;

            if (Class clz := typeSystem.classForName(className)) {
                return True, clz;
            } else if (Class clz := typeSystem.classForName(moduleName + "." + className)) {
                return True, clz;
            }
            return False;
        }

        /**
         * Set the verbose discovery logging flag.
         *
         * @param verbose  the `verbose` flag to use
         */
        Builder withVerboseLog(Boolean verbose)
                = new Builder(this.selectors, this.displayNameGenerator, verbose);

        /**
         * Set the `DisplayNameGenerator`.
         *
         * @param generator  the `DisplayNameGenerator` to use
         *
         * @return this `Builder`
         */
        Builder withDisplayNameGenerator(DisplayNameGenerator generator)
                = new Builder(this.selectors, generator, this.verbose);

        /**
         * Add a `Selector`.
         *
         * @param selectors  the `Selector`s to add
         *
         * @return this `Builder`
         */
        Builder withSelectors(Selector[] selectors)
                = new Builder(this.selectors.addAll(selectors),
                        this.displayNameGenerator, this.verbose);

        /**
         * Return a new builder that is a copy of this builder with the specified selector added.
         *
         * @param selector  the `Selector` to add
         *
         * @return a new builder that is a copy of this builder with the specified selector added
         */
        Builder withSelector(Selector selector)
                = new Builder(this.selectors.add(selector), this.displayNameGenerator, this.verbose);

        /**
         * @return a `DiscoveryConfiguration` built from this `Builder`
         */
        DiscoveryConfiguration build() = new DiscoveryConfiguration(this);
    }
}
