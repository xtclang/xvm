import telemetry.metrics.model.GaugeData;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.Measurement;
import telemetry.metrics.model.Metric;
import telemetry.metrics.model.NumberDataPoint;
import telemetry.metrics.model.SumData;
import telemetry.metrics.memory.InMemoryObservableCounter;
import telemetry.metrics.memory.InMemoryObservableGauge;
import telemetry.metrics.memory.InMemoryObservableUpDownCounter;

class ObservableInstrumentTest {

    static InstrumentDescriptor counterDesc = new InstrumentDescriptor("test.page.faults");
    static InstrumentDescriptor upDownDesc  = new InstrumentDescriptor("test.heap.used", unit = "By");
    static InstrumentDescriptor gaugeDesc   = new InstrumentDescriptor("test.temperature", unit = "Cel");

    // ----- ObservableCounter ---------------------------------------------------------------------

    @Test
    void observableCounterReturnsNullWithNoCallbacks() {
        InMemoryObservableCounter c = new InMemoryObservableCounter(counterDesc);
        assert !c.collectMetric();
    }

    @Test
    void observableCounterInvokesCallback() {
        InMemoryObservableCounter c = new InMemoryObservableCounter(counterDesc);
        c.observe(observe -> {
            observe(new Measurement(42.0));
        });

        assert Metric m := c.collectMetric();
        assert m.name == "test.page.faults";
        assert m.data.is(SumData);
        SumData data = m.data.as(SumData);
        assert data.isMonotonic;
        assert data.dataPoints.size == 1;
        assert data.dataPoints[0].value.as(Float64) == 42.0;
    }

    @Test
    void observableCounterUnregisterStopsCollection() {
        InMemoryObservableCounter c = new InMemoryObservableCounter(counterDesc);
        Closeable reg = c.observe(observe -> {
            observe(new Measurement(10.0));
        });

        assert Metric m1 := c.collectMetric();

        reg.close();

        assert !c.collectMetric() as "Callback should no longer be invoked after unregistration";
    }

    @Test
    void observableCounterInvokesMultipleCallbacks() {
        InMemoryObservableCounter c = new InMemoryObservableCounter(counterDesc);
        c.observe(observe -> { observe(new Measurement(100.0)); });
        c.observe(observe -> { observe(new Measurement(200.0)); });

        assert Metric m := c.collectMetric();
        assert m.data.as(SumData).dataPoints.size == 2;
    }

    @Test
    void observableCounterCallbackReportsMultipleMeasurements() {
        InMemoryObservableCounter c = new InMemoryObservableCounter(counterDesc);
        c.observe(observe -> {
            observe(new Measurement(10.0, ["region" = "us-east"]));
            observe(new Measurement(20.0, ["region" = "eu-west"]));
        });

        assert Metric m := c.collectMetric();
        assert m.data.as(SumData).dataPoints.size == 2;
    }

    // ----- ObservableUpDownCounter ---------------------------------------------------------------

    @Test
    void observableUpDownCounterInvokesCallback() {
        InMemoryObservableUpDownCounter c = new InMemoryObservableUpDownCounter(upDownDesc);
        c.observe(observe -> {
            observe(new Measurement(512000.0));
        });

        assert Metric m := c.collectMetric();
        SumData data = m.data.as(SumData);
        assert !data.isMonotonic;
        assert data.dataPoints[0].value.as(Float64) == 512000.0;
    }

    @Test
    void observableUpDownCounterUnregisterStopsCollection() {
        InMemoryObservableUpDownCounter c = new InMemoryObservableUpDownCounter(upDownDesc);
        Closeable reg = c.observe(observe -> { observe(new Measurement(1.0)); });
        c.collectMetric();
        reg.close();
        assert !c.collectMetric();
    }

    // ----- ObservableGauge -----------------------------------------------------------------------

    @Test
    void observableGaugeInvokesCallback() {
        InMemoryObservableGauge g = new InMemoryObservableGauge(gaugeDesc);
        g.observe(observe -> {
            observe(new Measurement(23.5));
        });

        assert Metric m := g.collectMetric();
        assert m.data.is(GaugeData);
        NumberDataPoint p = m.data.as(GaugeData).dataPoints[0];
        assert p.value.as(Float64) == 23.5;
    }

    @Test
    void observableGaugeUnregisterStopsCollection() {
        InMemoryObservableGauge g = new InMemoryObservableGauge(gaugeDesc);
        Closeable reg = g.observe(observe -> { observe(new Measurement(1.0)); });
        g.collectMetric();
        reg.close();
        assert !g.collectMetric();
    }
}
