/**
 * A synchronous instrument that records increments and decrements.
 *
 * Use for values that can rise and fall (e.g. active requests, queue depth). Backed by
 * a [SumData] with `isMonotonic = False`.
 */
interface UpDownCounter {
    @RO InstrumentDescriptor descriptor;

    /**
     * `False` when the SDK has disabled this instrument; callers may skip measurement
     * work entirely when this is `False`.
     */
    @RO Boolean enabled;

    /**
     * Adjusts the counter by `value`. `value` may be positive or negative.
     */
    void add(NumberValue value, Map<String, AnyValue> attributes = []);
}
