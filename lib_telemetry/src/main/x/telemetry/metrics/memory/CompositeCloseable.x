/**
 * A [Closeable] that closes multiple inner [Closeable] instances as one unit.
 * Used by fan-out observable instruments to return a single handle for unregistering
 * N underlying callback registrations.
 */
service CompositeCloseable(Closeable[] handles) implements Closeable {
    @Override
    void close(Exception? cause = Null) {
        for (Closeable h : handles) {
            h.close(cause);
        }
    }
}
