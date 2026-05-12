/**
 * The telemetry context propagates trace state and baggage through a call chain without
 * explicit parameter threading.
 *
 * Contexts are immutable: to add or replace a value, create a new Context via [with].
 * Use [Telemetry.attach] to make a context active for the current scope.
 */
const Context {
    static Context Empty = new Context([]);

    construct(Tuple<String, Object>[] entries = []) {
        this.entries = entries;
    }

    Tuple<String, Object>[] entries;

    /**
     * Returns a new context containing all values from this context plus the given key-value pair.
     */
    Context with(String key, Object value) {
        return new Context(entries + [Tuple:(key, value)]);
    }
}
