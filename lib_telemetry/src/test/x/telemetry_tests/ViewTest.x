import telemetry.Resource;
import telemetry.metrics.Aggregation;
import telemetry.metrics.Counter;
import telemetry.metrics.Gauge;
import telemetry.metrics.model.GaugeData;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.InstrumentSelector;
import telemetry.metrics.InstrumentType;
import telemetry.metrics.model.Metric;
import telemetry.metrics.model.NumberDataPoint;
import telemetry.metrics.model.ResourceMetrics;
import telemetry.metrics.StreamConfig;
import telemetry.metrics.model.SumData;
import telemetry.metrics.View;
import telemetry.metrics.memory.InMemoryMeterProvider;

class ViewTest {

    static InstrumentDescriptor hits   = new InstrumentDescriptor("hits",   unit = "{hit}");
    static InstrumentDescriptor bytes  = new InstrumentDescriptor("bytes",  unit = "By");
    static InstrumentDescriptor reqs   = new InstrumentDescriptor("reqs",   unit = "{req}");

    // ----- helper --------------------------------------------------------------------------------

    private static NumberDataPoint firstPoint(ResourceMetrics[] batch) {
        return batch[0].scopeMetrics[0].metrics[0].data.as(SumData).dataPoints[0];
    }

    private static Metric[] allMetrics(ResourceMetrics[] batch) {
        return batch[0].scopeMetrics[0].metrics;
    }

    // ----- no views (default path) ---------------------------------------------------------------

    @Test
    void noViewsDefaultBehavior() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider();
        provider.getMeter("lib").createCounter(hits).add(5.0);

        ResourceMetrics[] batch = provider.collect();
        assert batch[0].scopeMetrics[0].metrics.size == 1;
        assert batch[0].scopeMetrics[0].metrics[0].name == "hits";
    }

    // ----- Drop ----------------------------------------------------------------------------------

    @Test
    void dropByExactNameSuppressesMetric() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"), new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        provider.getMeter("lib").createCounter(hits).add(3.0);

        ResourceMetrics[] batch = provider.collect();
        assert allMetrics(batch).empty as "Drop view must suppress the metric";
    }

    @Test
    void dropWildcardSuppressesAll() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "*"), new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        var meter = provider.getMeter("lib");
        meter.createCounter(hits).add(1.0);
        meter.createCounter(bytes).add(2.0);

        ResourceMetrics[] batch = provider.collect();
        assert allMetrics(batch).empty as "Wildcard Drop must suppress all metrics";
    }

    @Test
    void dropNonMatchingPassesThrough() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "other"), new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        provider.getMeter("lib").createCounter(hits).add(7.0);

        ResourceMetrics[] batch = provider.collect();
        assert allMetrics(batch).size == 1 as "Non-matching Drop must not suppress";
    }

    @Test
    void dropByTypeSuppressesOnlyThatType() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(type = InstrumentType.Counter),
                     new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        var meter = provider.getMeter("lib");
        meter.createCounter(hits).add(1.0);
        meter.createGauge(reqs).record(2.0);

        ResourceMetrics[] batch = provider.collect();
        Metric[] metrics = allMetrics(batch);
        assert metrics.size == 1 as "Only counters must be suppressed";
        assert metrics[0].data.is(GaugeData) as "Gauge must still be exported";
    }

    @Test
    void dropByUnitSuppressesMatchingUnit() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(unit = "By"),
                     new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        var meter = provider.getMeter("lib");
        meter.createCounter(bytes).add(100.0);
        meter.createCounter(hits).add(5.0);

        ResourceMetrics[] batch = provider.collect();
        Metric[] metrics = allMetrics(batch);
        assert metrics.size == 1;
        assert metrics[0].name == "hits";
    }

    @Test
    void dropByMeterNameSuppressesOnlyThatMeter() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(meterName = "lib-a"),
                     new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        provider.getMeter("lib-a").createCounter(hits).add(1.0);
        provider.getMeter("lib-b").createCounter(hits).add(2.0);

        ResourceMetrics[] batch = provider.collect();
        // Two scopes: lib-a suppressed, lib-b not
        Int total = 0;
        for (var sm : batch[0].scopeMetrics) {
            total += sm.metrics.size;
        }
        assert total == 1 as "Only lib-a instruments must be suppressed";
    }

    // ----- glob name matching --------------------------------------------------------------------

    @Test
    void globStarMatchesPrefix() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "http.*"),
                     new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        var meter = provider.getMeter("lib");
        meter.createCounter(new InstrumentDescriptor("http.requests")).add(1.0);
        meter.createCounter(new InstrumentDescriptor("http.errors")).add(1.0);
        meter.createCounter(new InstrumentDescriptor("db.queries")).add(1.0);

        ResourceMetrics[] batch = provider.collect();
        Metric[] metrics = allMetrics(batch);
        assert metrics.size == 1;
        assert metrics[0].name == "db.queries";
    }

    @Test
    void globQuestionMarkMatchesSingleChar() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "io.??t"),
                     new StreamConfig(aggregation = Aggregation.Drop)),
        ]);
        var meter = provider.getMeter("lib");
        meter.createCounter(new InstrumentDescriptor("io.put")).add(1.0);
        meter.createCounter(new InstrumentDescriptor("io.get")).add(1.0);
        meter.createCounter(new InstrumentDescriptor("io.delete")).add(1.0);  // too long

        ResourceMetrics[] batch = provider.collect();
        Metric[] metrics = allMetrics(batch);
        assert metrics.size == 1;
        assert metrics[0].name == "io.delete";
    }

    // ----- stream config — rename ----------------------------------------------------------------

    @Test
    void streamRenameProducesRenamedMetric() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"),
                     new StreamConfig(name = "renamed.hits")),
        ]);
        provider.getMeter("lib").createCounter(hits).add(4.0);

        ResourceMetrics[] batch = provider.collect();
        assert allMetrics(batch).size == 1;
        assert allMetrics(batch)[0].name == "renamed.hits";
    }

    @Test
    void streamRedescribeOverridesDescription() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"),
                     new StreamConfig(description = "Overridden description")),
        ]);
        provider.getMeter("lib").createCounter(hits).add(1.0);

        ResourceMetrics[] batch = provider.collect();
        assert allMetrics(batch)[0].description == "Overridden description";
    }

    // ----- stream config — attribute filter -----------------------------------------------------

    @Test
    void attributeAllowListFiltersKeys() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"),
                     new StreamConfig(attributeKeys = ["region"])),
        ]);
        provider.getMeter("lib").createCounter(hits)
                .add(1.0, ["region" = "us-east", "env" = "prod"]);

        ResourceMetrics[] batch = provider.collect();
        NumberDataPoint p = firstPoint(batch);
        assert p.attributes.keys.contains("region") as "region must be kept";
        assert !p.attributes.keys.contains("env")   as "env must be filtered out";
    }

    @Test
    void attributeExcludeListRemovesKeys() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"),
                     new StreamConfig(excludedAttributeKeys = ["env"])),
        ]);
        provider.getMeter("lib").createCounter(hits)
                .add(1.0, ["region" = "us-east", "env" = "prod"]);

        ResourceMetrics[] batch = provider.collect();
        NumberDataPoint p = firstPoint(batch);
        assert p.attributes.keys.contains("region") as "region must be kept";
        assert !p.attributes.keys.contains("env")   as "env must be excluded";
    }

    // ----- multiple matching views → multiple streams -------------------------------------------

    @Test
    void twoViewsTwoStreams() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"), new StreamConfig(name = "hits.a")),
            new View(new InstrumentSelector(name = "hits"), new StreamConfig(name = "hits.b")),
        ]);
        provider.getMeter("lib").createCounter(hits).add(9.0);

        ResourceMetrics[] batch = provider.collect();
        Metric[] metrics = allMetrics(batch);
        assert metrics.size == 2 as "Two views must produce two output streams";
        // Both streams must be present (order not guaranteed)
        Boolean hasA = False;
        Boolean hasB = False;
        for (Metric m : metrics) {
            if (m.name == "hits.a") { hasA = True; }
            if (m.name == "hits.b") { hasB = True; }
        }
        assert hasA && hasB;
    }

    @Test
    void mixedDropAndNonDropOnlyEmitsNonDrop() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(views = [
            new View(new InstrumentSelector(name = "hits"), new StreamConfig(aggregation = Aggregation.Drop)),
            new View(new InstrumentSelector(name = "hits"), new StreamConfig(name = "hits.kept")),
        ]);
        provider.getMeter("lib").createCounter(hits).add(5.0);

        ResourceMetrics[] batch = provider.collect();
        Metric[] metrics = allMetrics(batch);
        assert metrics.size == 1;
        assert metrics[0].name == "hits.kept";
    }
}
