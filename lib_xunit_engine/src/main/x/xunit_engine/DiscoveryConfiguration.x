import discovery.Selector;

import xunit.DisplayNameGenerator;
import xunit.MethodOrFunction;

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
    private construct(Builder builder) {
        this.displayNameGenerator = builder.displayNameGenerator;
        this.selectors            = builder.selectors.freeze(False);
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
     * Return this `DiscoveryConfiguration` as a `Builder`.
     */
    Builder asBuilder() {
        return new Builder(this);
    }

    /**
     * A convenience method to return the display name for a
     * `Class` using the configured `DisplayNameGenerator`.
     *
     * @param clz  the `Class` to obtain the display name for
     *
     * @return the human readable display name for the `Class`
     */
    String displayNameFor(Class clz) {
        return displayNameGenerator.nameForClass(clz);
    }

    /**
     * A convenience method to return the display name for a
     * `Class` using the configured `DisplayNameGenerator`.
     *
     * @param clz     the `Class` to obtain the display name for
     * @param method  the test method or function to obtain the display name for
     *
     * @return the human readable display name for the `Class`
     */
    String displayNameFor(Class clz, MethodOrFunction method) {
        return displayNameGenerator.nameForMethod(clz, method);
    }

    // ----- factory methods -----------------------------------------------------------------------

    /**
     * Create a default `DiscoveryConfiguration`.
     */
    static DiscoveryConfiguration create() {
        return builder().build();
    }

    /**
     * Create a `DiscoveryConfiguration` builder.
     */
    static Builder builder() {
        return new Builder();
    }

    // ----- inner class: Builder ------------------------------------------------------------------

    /**
     * A `DiscoveryConfiguration` builder.
     */
    static class Builder {
        /**
         * Create a `Builder`.
         */
        construct() {
            selectors = new Array();
        }

        /**
         * Create a `Builder`.
         *
         * @param config  the `DiscoveryConfiguration` to use to create the builder
         */
        construct(DiscoveryConfiguration config) {
            this.displayNameGenerator = config.displayNameGenerator;
            this.selectors            = new Array();
            this.selectors.addAll(config.selectors);
        }

        /**
         * The `DisplayNameGenerator` to use.
         */
        private DisplayNameGenerator displayNameGenerator = DisplayNameGenerator.Default;

        /**
         * The `Selector`s to use to discover tests.
         */
        private Selector[] selectors;

        /**
         * Set the `DisplayNameGenerator`.
         *
         * @param generator  the `DisplayNameGenerator` to use
         *
         * @return this `Builder`
         */
        Builder withDisplayNameGenerator(DisplayNameGenerator generator) {
            displayNameGenerator = generator;
            return this;
        }

        /**
         * Add a `Selector`.
         *
         * @param selector  the `Selector` to add
         *
         * @return this `Builder`
         */
        Builder withSelector(Selector selector) {
            selectors.add(selector);
            return this;
        }

        /**
         * Build the `DiscoveryConfiguration`.
         *
         * @return a `DiscoveryConfiguration` built from this `Builder`
         */
        DiscoveryConfiguration build() {
            return new DiscoveryConfiguration(this);
        }
    }
}
