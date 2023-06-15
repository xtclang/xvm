/**
 * A `Observer` receives notifications when a resource has been disposed.
 */
interface Observer<Resource> {
    /**
     * Called by a `ResourceRegistry` when a resource has been unregistered,
     * without being closed.
     *
     * @param resource  the resource being unregistered
     */
    void onUnregister(Resource resource) {
    }

    /**
     * Called by a `ResourceRegistry` when a resource is being closed.
     *
     * @param resource  the resource being closed
     * @param cause     (optional) an exception that occurred that triggered the close
     */
    void onClosing(Resource resource, Exception? cause = Null) {
    }

    /**
     * Called by a `ResourceRegistry` when a resource is being closed.
     *
     * @param resource  the resource that has been closed
     * @param cause     (optional) an exception that occurred that triggered the close
     */
    void onClosed(Resource resource, Exception? cause = Null) {
    }
}

