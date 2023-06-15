/**
 * A `Selector` is used by the discovery mechanism to select test fixtures for execution.
 */
interface Selector
        extends Service {

    /**
     * The parent identifier for models and selectors discovered by this selector.
     */
    @RO UniqueId? parentId;

    /**
     * Discover test fixtures for execution.
     *
     * @param config  the discovery configuration
     *
     * @return the `Model`s representing the test fixtures discovered by this selector
     */
    immutable Model[] select(DiscoveryConfiguration config);
}