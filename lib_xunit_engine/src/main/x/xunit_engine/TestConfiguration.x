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
     * Return this `TestConfiguration` as a `Builder`.
     */
    Builder asBuilder() {
        return new Builder(this);
    }

    /**
     * Create a default `TestConfiguration`.
     */
    static TestConfiguration create() {
        return builder().build();
    }

    /**
     * Create an `TestConfiguration` builder.
     */
    static Builder builder() {
        return new Builder();
    }

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
         * Build the `TestConfiguration`.
         *
         * @return an `TestConfiguration` built from this `Builder`
         */
        TestConfiguration build() {
            return new TestConfiguration(this);
        }
    }
}
