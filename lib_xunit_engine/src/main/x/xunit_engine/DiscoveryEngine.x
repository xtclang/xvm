import discovery.DefaultDiscoveryEngine;

/**
 * A `DiscoveryEngine` is a service that is used to discover the
 * test fixtures to be executed.
 */
interface DiscoveryEngine
        extends Service {
    /**
     * Run the discovery engine.
     *
     * @param configuration  the `DiscoveryConfiguration` to use to control
     *                       test fixture discovery.
     *
     * @return  the test fixture `Model`s discovered
     */
    Model[] discover(DiscoveryConfiguration configuration);

    // ----- factory methods -----------------------------------------------------------------------

    /**
     * Create an instance of the default `DiscoveryEngine`.
     *
     * @return an instance of the default `DiscoveryEngine`
     */
    static DiscoveryEngine create() {
        return new DefaultDiscoveryEngine();
    }
}