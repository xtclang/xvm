import metrics.MeterProvider;

/**
 * An annotation used to mark a module as instrumented.
 */
annotation Instrumented(Attributes attributes = [],
                        String?    schemaUrl  = Null)
        into module {

    @Lazy MeterProvider meterProvider.calc() {
        return Telemetry.ensureMeterProvider(this);
    }


}
