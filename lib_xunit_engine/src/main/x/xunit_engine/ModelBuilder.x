/**
 * A builder of `Model` instances.
 */
interface ModelBuilder<ModelType>
    extends Service {

    /**
     * The `UniqueId` of the `Model` this `Builder` builds.
     */
    @RO UniqueId uniqueId;

    /**
     * Return the `Model`s for the element in the test module/package/class/method hierarchy
     * represented by the `UniqueId.
     *
     * @param  configuration  the `DiscoveryConfiguration` to use
     * @param  children       the children of the `Model` being built
     *
     * @return a `Model` with the specified children
     */
    ModelType build(DiscoveryConfiguration configuration, Model[] children);
}
