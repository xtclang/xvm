/**
 * The configuration to control test execution.
 */
const ExecutionConfiguration {
    /**
     * Create an `ExecutionConfiguration`.
     *
     * @param builder  the `Builder` to create the config from
     */
    construct (Builder builder) {
    }

    /**
     * Return this `ExecutionConfiguration` as a `Builder`.
     */
    Builder asBuilder() = new Builder(this);

    /**
     * Create an `ExecutionConfiguration` builder.
     */
    static Builder builder() = new Builder();

    /**
     * An `ExecutionConfiguration` builder.
     */
    static const Builder {

        /**
         * Create a `Builder`.
         */
        construct() {
        }

        /**
         * Create a `Builder` from the specified execution configuration..
         *
         * @param config  the `ExecutionConfiguration` to use to create the builder
         */
        construct(ExecutionConfiguration config) {
        }

        /**
         * Build the `ExecutionConfiguration`.
         *
         * @return an `ExecutionConfiguration` built from this `Builder`
         */
        ExecutionConfiguration build() = new ExecutionConfiguration(this);
    }
}