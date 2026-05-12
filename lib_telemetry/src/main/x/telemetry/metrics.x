package metrics {
    /**
     * A typedef to represent valid metric number types.
     */
    typedef Int | Float64 as NumberValue;

    /**
     * A callback supplied to an asynchronous instrument. The SDK invokes it during each
     * collection cycle and passes an `observe` function; the callback calls `observe` once
     * per distinct attribute set it wishes to report.
     */
    typedef function void (function void (Measurement)) as MetricCallback;
}
