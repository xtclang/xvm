
/**
 * A `TestEngine` is the main service of XUnit and is responsible
 * for the discovery and execution of tests.
 */
interface TestEngine
        extends Service {
    /**
     * Discover the test `Model` to execute.
     *
     * @param config     the `DiscoveryConfiguration` to use to control
     *                   test discovery
     * @param selectors  the initial set of `Selector`s to use to discover tests
     */
    Model discover(DiscoveryConfiguration config, Selector[] selectors);

    /**
     * Execute a test `Model`.
     *
     * @param model  the test `Model` to execute
     */
    void execute(Model model);
}
