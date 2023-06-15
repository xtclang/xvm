import xunit.selectors.ModuleSelector;

/**
 * The configuration used to determine how test fixtures and tests are discovered
 * for a specific test execution.
 *
 * A test discovery configuration may be to discover just a single test method, or
 * discover all tests in a class, package or module, or all tests of a specific
 * test group, or a combination of any of these.
 */
const DiscoveryConfiguration(DisplayNameGenerator displayNameGenerator = DisplayNameGenerator.Default) {
    /**
     * Return this `DiscoveryConfiguration` as a `Builder`.
     */
    Builder asBuilder() {
        return new Builder(this);
    }

    /**
     * Create a default `DiscoveryConfiguration`.
     */
    static DiscoveryConfiguration create() {
        return builder().build();
    }

    /**
     * Create a `DiscoveryConfiguration` builder to discover test fixtures using the specified `Selector`s.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A `DiscoveryConfiguration` builder.
     *
     * @param selectors  the `Selector`s to use to discover test fixtures
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
         * @param config  the `DiscoveryConfiguration` to use to create the builder
         */
        construct(DiscoveryConfiguration config) {
            this.displayNameGenerator = config.displayNameGenerator;
        }

        DisplayNameGenerator displayNameGenerator = DisplayNameGenerator.Default;

        Builder withDisplayNameGenerator(DisplayNameGenerator generator) {
            displayNameGenerator = generator;
            return this;
        }

        DiscoveryConfiguration build() {
            return new DiscoveryConfiguration(displayNameGenerator);
        }
    }
}
