/**
 * A no-op [MeterProvider] that returns [NoOpMeter] instances for every scope.
 *
 * Use [Global] as the default provider when no SDK has been configured, ensuring that
 * instrumented code compiles and runs without a backend attached.
 */
const NoOpMeterProvider
        implements MeterProvider {

    static NoOpMeterProvider Global = new NoOpMeterProvider();


    @Override
    Meter getMeter(InstrumentationScope scope) {
        return new NoOpMeter(scope);
    }

    @Override
    void forceFlush() {}

    @Override
    void shutdown() {}
}
