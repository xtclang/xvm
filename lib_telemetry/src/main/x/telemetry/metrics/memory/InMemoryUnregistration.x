/**
 * A [Closeable] returned by `observe()` on in-memory asynchronous instruments. Closing
 * it removes the associated callback from the instrument's collection cycle.
 */
service InMemoryUnregistration(InMemoryObservableInstrument instrument, Int id)
        implements Closeable {

    @Override
    void close(Exception? cause = Null) {
        instrument.unobserve(id);
    }
}
