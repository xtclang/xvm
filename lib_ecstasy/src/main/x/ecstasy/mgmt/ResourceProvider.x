/**
 * Represents the source of injected resources.
 */
interface ResourceProvider
        extends Closeable {

    import annotations.InjectedRef.Options;

    /**
     * While we can only postulate that the compile-time type of a resource is Object, at run-time
     * it is known to be an immutable Const or a Service.
     */
    typedef Object                                  as Resource;
    typedef (Resource | function Resource(Options)) as Supplier;

    /**
     * Obtain a resource supplier for specified type and name. Most commonly, a failure of the
     * provider to return a resource supplier (throwing an exception) will fail to load or
     * terminate the requesting container.
     *
     * In a scenario, where a parent container chooses to allow the child container to load, but
     * wants to restrict the use of some injected resources, the supplier may throw a run-time
     * exception, possibly causing the termination of the container.
     */
    Supplier getResource(Type type, String name);

    /**
     * At the point that the container (for which this ResourceProvider exists) is shut down or
     * killed, this method will be invoked to allow closing any resources that this provider has
     * supplied to that container.
     *
     * Common implementations of this method are expected to invoke the `close()` method on all
     * previously supplied resources that implement the [Closeable] interface.
     */
    @Override
    void close(Exception? cause = Null) {}
}