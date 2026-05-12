/**
 * A no-op [Closeable] returned by asynchronous no-op instruments when a callback is
 * registered. Closing it has no effect.
 */
const NoOpCloseable
        implements Closeable {

    static NoOpCloseable Instance = new NoOpCloseable();

    @Override
    void close(Exception? cause = Null) {}
}
