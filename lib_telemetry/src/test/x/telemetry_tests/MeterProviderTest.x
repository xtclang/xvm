import telemetry.Resource;
import telemetry.metrics.model.AggregationTemporality;
import telemetry.metrics.Counter;
import telemetry.metrics.Gauge;
import telemetry.metrics.Histogram;
import telemetry.metrics.model.HistogramData;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.Metric;
import telemetry.metrics.model.ResourceMetrics;
import telemetry.metrics.model.ScopeMetrics;
import telemetry.metrics.model.SumData;
import telemetry.metrics.memory.InMemoryMeterProvider;

class MeterProviderTest {

    static Resource testResource = new Resource([Resource.ServiceNameKey = "test-service"]);

    // ----- provider / meter wiring ---------------------------------------------------------------

    @Test
    void collectReturnsSingleResourceMetricsEntry() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        ResourceMetrics[] batch = provider.collect();
        assert batch.size == 1;
        assert batch[0].resource == testResource;
    }

    @Test
    void getMeterReturnsSeparateScopePerCall() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        provider.getMeter("scope-a", version = "1.0");
        provider.getMeter("scope-b", version = "2.0");

        ResourceMetrics[] batch = provider.collect();
        assert batch[0].scopeMetrics.size == 2;
        assert batch[0].scopeMetrics[0].scope.name == "scope-a";
        assert batch[0].scopeMetrics[1].scope.name == "scope-b";
    }

    // ----- instrument deduplication --------------------------------------------------------------

    @Test
    void createCounterReturnsSameInstanceForSameName() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        var meter = provider.getMeter("my-lib");
        var desc  = new InstrumentDescriptor("http.requests", unit = "{request}");

        Counter c1 = meter.createCounter(desc);
        Counter c2 = meter.createCounter(desc);
        assert &c1 == &c2 as "Same descriptor must return the same Counter instance";
    }

    @Test
    void deduplicatedCounterAccumulatesAcrossBothReferences() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        var meter = provider.getMeter("my-lib");
        var desc  = new InstrumentDescriptor("http.requests", unit = "{request}");

        Counter c1 = meter.createCounter(desc);
        Counter c2 = meter.createCounter(desc);
        c1.add(3.0);
        c2.add(7.0);

        ResourceMetrics[] batch = provider.collect();
        SumData sum = batch[0].scopeMetrics[0].metrics[0].data.as(SumData);
        assert sum.dataPoints.size == 1;
        assert sum.dataPoints[0].value.as(Float64) == 10.0;
    }

    @Test
    void differentUnitProducesDifferentCounter() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        var meter = provider.getMeter("my-lib");

        Counter c1 = meter.createCounter(new InstrumentDescriptor("io.bytes", unit = "By"));
        Counter c2 = meter.createCounter(new InstrumentDescriptor("io.bytes", unit = "kBy"));
        assert &c1 != &c2 as "Different units must produce distinct Counter instances";
    }

    @Test
    void sameNameDifferentInstrumentTypeAreSeparate() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        var meter = provider.getMeter("my-lib");
        var desc  = new InstrumentDescriptor("requests", unit = "{request}");

        Counter c = meter.createCounter(desc);
        Histogram h = meter.createHistogram(desc);
        c.add(1.0);
        h.record(2.0);

        ResourceMetrics[] batch = provider.collect();
        assert batch[0].scopeMetrics[0].metrics.size == 2
                as "Counter and Histogram with the same descriptor must register independently";
    }

    // ----- counter end-to-end --------------------------------------------------------------------

    @Test
    void collectsCounterFromMeter() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        Counter counter = provider.getMeter("my-lib")
            .createCounter(new InstrumentDescriptor("http.requests", unit = "{request}"));

        counter.add(5.0);
        counter.add(3.0);

        ResourceMetrics[] batch = provider.collect();
        ScopeMetrics      sm    = batch[0].scopeMetrics[0];
        assert sm.scope.name == "my-lib";
        assert sm.metrics.size == 1;

        Metric m = sm.metrics[0];
        assert m.name == "http.requests";
        assert m.unit == "{request}";
        assert m.data.is(SumData);
        assert m.data.as(SumData).isMonotonic;
        assert m.data.as(SumData).dataPoints.size == 1;
        assert m.data.as(SumData).dataPoints[0].value.as(Float64) == 8.0;
    }

    @Test
    void deltaCollectClearsAccumulator() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        Counter counter = provider.getMeter("lib")
            .createCounter(new InstrumentDescriptor("requests"));

        counter.add(10.0);
        provider.collect(); // first collect — clears delta state

        // No new additions — second collect should produce no metrics
        ResourceMetrics[] batch = provider.collect();
        assert batch[0].scopeMetrics[0].metrics.empty;
    }

    // ----- histogram end-to-end ------------------------------------------------------------------

    @Test
    void collectsHistogramFromMeter() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        Histogram h = provider.getMeter("my-lib").createHistogram(
            new InstrumentDescriptor("latency", unit = "ms"),
            bucketBoundaries = [10.0, 100.0]);

        h.record(5.0);
        h.record(50.0);
        h.record(500.0);

        ScopeMetrics sm = provider.collect()[0].scopeMetrics[0];
        assert sm.metrics.size == 1;

        Metric m = sm.metrics[0];
        assert m.data.is(HistogramData);
        assert m.data.as(HistogramData).dataPoints[0].count == 3;
    }

    // ----- temporality flow ----------------------------------------------------------------------

    @Test
    void cumulativeTemporalityFlowsToCounter() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(
            resource    = testResource,
            temporality = AggregationTemporality.Cumulative);

        Counter counter = provider.getMeter("lib")
            .createCounter(new InstrumentDescriptor("events"));

        counter.add(5.0);
        provider.collect(); // first cycle

        counter.add(3.0);
        ResourceMetrics[] batch = provider.collect(); // second cycle

        // In cumulative mode the running total is 5 + 3 = 8
        Metric m = batch[0].scopeMetrics[0].metrics[0];
        assert m.data.as(SumData).aggregationTemporality == AggregationTemporality.Cumulative;
        assert m.data.as(SumData).dataPoints[0].value.as(Float64) == 8.0;
    }

    @Test
    void cumulativeTemporalityFlowsToHistogram() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(
            resource    = testResource,
            temporality = AggregationTemporality.Cumulative);

        Histogram h = provider.getMeter("lib").createHistogram(
            new InstrumentDescriptor("sizes", unit = "By"));

        h.record(100.0);
        provider.collect(); // first cycle

        h.record(200.0);
        ResourceMetrics[] batch = provider.collect(); // second cycle

        // Cumulative: count = 1 + 1 = 2
        assert batch[0].scopeMetrics[0].metrics[0]
            .data.as(HistogramData).dataPoints[0].count == 2;
    }

    // ----- multi-instrument collection ----------------------------------------------------------

    @Test
    void collectsMultipleInstrumentTypesFromOneMeter() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);
        var meter   = provider.getMeter("multi-lib");
        Counter counter = meter.createCounter(new InstrumentDescriptor("reqs"));
        Gauge   gauge   = meter.createGauge(new InstrumentDescriptor("cpu"));
        Histogram hist  = meter.createHistogram(new InstrumentDescriptor("latency", unit = "ms"));

        counter.add(1.0);
        gauge.record(0.75);
        hist.record(42.0);

        ScopeMetrics sm = provider.collect()[0].scopeMetrics[0];
        assert sm.metrics.size == 3;
    }
}
