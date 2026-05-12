/**
 * A synchronous instrument that reports the current absolute value of a measurement.
 *
 * Use for non-additive measurements that represent a snapshot rather than an
 * accumulation (e.g. CPU utilisation percentage, current fan speed). Backed by
 * [GaugeData].
 */
interface Gauge {
    @RO InstrumentDescriptor descriptor;

    /**
     * `False` when the SDK has disabled this instrument; callers may skip measurement
     * work entirely when this is `False`.
     */
    @RO Boolean enabled;

    /**
     * Records the current absolute value of the measurement.
     */
    void record(NumberValue value, Map<String, AnyValue> attributes = []);
}
