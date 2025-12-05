/**
 * A builder of `Model` instances.
 */
interface ModelBuilder
    extends Service
    extends Stringable {

    /**
     * The `UniqueId` of the `Model` this `Builder` builds.
     */
    @RO UniqueId uniqueId;

    /**
     * A flag indicating whether this builder builds a container model.
     */
    @RO Boolean isContainer;

    /**
     * Return the `Model` for the element in the test hierarchy represented by the `UniqueId.
     *
     * @param  configuration  the `DiscoveryConfiguration` to use
     * @param  children       the children of the `Model` being built
     *
     * @return a `Model` with the specified children
     */
    Model build(DiscoveryConfiguration configuration, Model[] children);
}
