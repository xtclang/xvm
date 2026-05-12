/**
 * Internal interface for in-memory asynchronous instruments that support unregistering
 * a previously registered callback via an integer slot ID.
 */
interface InMemoryObservableInstrument {
    void unobserve(Int id);
}
