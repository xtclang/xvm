import annotations.Instrumented;

import metrics.MeterProvider;
import metrics.model.AggregationTemporality;

import metrics.memory.InMemoryMeterProvider;

import metrics.noop.NoOpMeterProvider;

/**
 * The singleton Telemetry service.
 */
static service Telemetry {

    /**
     * A map of the default meter providers for modules.
     */
    private Map<Module, MeterProvider> meterProviders = new HashMap();

    MeterProvider ensureMeterProvider(Module m) {
        if (!metrics.config.enabled) {
            return NoOpMeterProvider.Global;
        }

        return meterProviders.computeIfAbsent(m, () -> {
            Resource               resource    = resourceFor(m);
            AggregationTemporality temporality = metrics.config.aggregationTemporality;

            return new InMemoryMeterProvider(resource, temporality=temporality);
        });
    }

    private Resource resourceFor(Module m) {

        Attributes attributes = new HashMap();
        if (m.is(Instrumented)) {
            attributes.putAll(m.attributes);
        }

        attributes.putIfAbsent(Resource.ServiceNameKey, m.qualifiedName);
        attributes.putIfAbsent(Resource.ServiceVersionKey, m.version.toString());
        attributes.put(Resource.TelemetrySdkNameKey, ecstasy.as(Module).qualifiedName);
        attributes.put(Resource.TelemetrySdkLanguageKey, "Ecstasy");
        attributes.put(Resource.TelemetrySdkVersionKey, ecstasy.as(Module).version.toString());

        return new Resource(attributes.makeImmutable());
    }
}