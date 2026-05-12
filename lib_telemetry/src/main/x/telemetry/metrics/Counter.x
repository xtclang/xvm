/**
 * A synchronous instrument that records non-negative increments.
 *
 * Use for values that only go up (e.g. requests served, bytes sent). Backed by a
 * [SumData] with `isMonotonic = True`.
 */
interface Counter {
    @RO InstrumentDescriptor descriptor;

    /**
     * `False` when the SDK has disabled this instrument; callers may skip measurement
     * work entirely when this is `False`.
     */
    @RO Boolean enabled;

    /**
     * Increments the counter by `value`. `value` must be non-negative.
     */
    void add(NumberValue value, Map<String, AnyValue> attributes = []);

    /**
     * Increments the counter by one.
     */
    void inc(Map<String, AnyValue> attributes = []) {
        add(Int:1, attributes);
    }
}
