/**
 * The Service interface represents a control point for all asynchronous services.
 * Specifically, a
 */
interface Service
    {
    @ro Boolean running;

    /**
     * Returns true if the service is processing.
     */
    @ro Boolean busy;

    @ro Boolean hasBacklog;
    @ro Int backlogDepth;

    Boolean reentrant = true;

    /**
     * Attempt to terminate the Service gracefully by asking it to shut down itself.
     * A client that has a reference to the Service may shut it down, or the service
     * can shut itself down, or the service can be shut down by the runtime when the
     * service is no longer reachable.
     * <p>
     * There is a duality of context for this method.
     */
    Boolean shutdown();

    /**
     * Forcibly terminate the Service without giving it a chance to shut down gracefully.
     */
    Void kill();

    @ro Boolean terminated;
    }
