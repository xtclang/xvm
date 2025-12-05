/**
 * The configuration to control test execution.
 */
const ExecutionConfiguration {
    /**
     * Create an `ExecutionConfiguration`.
     *
     * @param builder  the `Builder` to create the config from
     */
    private construct (Builder builder) {
    }

    /**
     * Return this `ExecutionConfiguration` as a `Builder`.
     */
    Builder asBuilder() {
        return new Builder(this);
    }

    /**
     * Create a default `ExecutionConfiguration`.
     */
    static ExecutionConfiguration create() {
        return builder().build();
    }

    /**
     * Create an `ExecutionConfiguration` builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * An `ExecutionConfiguration` builder.
     */
    static class Builder {
        /**
         * Create a `Builder`.
         */
        construct() {
        }

        /**
         * Create a `Builder`.
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
        ExecutionConfiguration build() {
            return new ExecutionConfiguration(this);
        }
    }
}