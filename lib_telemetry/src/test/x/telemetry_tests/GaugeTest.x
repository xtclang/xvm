import telemetry.metrics.model.GaugeData;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.Metric;
import telemetry.metrics.model.NumberDataPoint;
import telemetry.metrics.memory.InMemoryGauge;
import telemetry.metrics.memory.LastValueAggregator;

class GaugeTest {

    static InstrumentDescriptor descriptor =
        new InstrumentDescriptor("test.cpu.utilization", unit = "1");

    @Test
    void returnsNullWhenNothingRecorded() {
        LastValueAggregator agg = new LastValueAggregator(descriptor);
        assert !agg.collectMetric();
    }

    @Test
    void recordsSingleValue() {
        LastValueAggregator agg = new LastValueAggregator(descriptor);
        InMemoryGauge       g   = new InMemoryGauge(descriptor, agg);
        g.record(0.75);

        assert Metric m := agg.collectMetric();
        assert m.name == "test.cpu.utilization";
        assert m.data.is(GaugeData);

        NumberDataPoint p = m.data.as(GaugeData).dataPoints[0];
        assert p.value.is(Float64);
        assert p.value.as(Float64) == 0.75;
    }

    @Test
    void retainsLastValueForSameAttributeSet() {
        LastValueAggregator agg = new LastValueAggregator(descriptor);
        InMemoryGauge       g   = new InMemoryGauge(descriptor, agg);
        g.record(0.5);
        g.record(0.9);

        assert Metric m := agg.collectMetric();
        GaugeData data = m.data.as(GaugeData);
        assert data.dataPoints.size == 1;
        assert data.dataPoints[0].value.as(Float64) == 0.9;
    }

    @Test
    void producesOneDataPointPerAttributeSet() {
        LastValueAggregator agg = new LastValueAggregator(descriptor);
        InMemoryGauge       g   = new InMemoryGauge(descriptor, agg);
        g.record(0.5, ["cpu" = "0"]);
        g.record(0.9, ["cpu" = "1"]);

        assert Metric m := agg.collectMetric();
        assert m.data.as(GaugeData).dataPoints.size == 2;
    }

    @Test
    void resetsAfterCollect() {
        LastValueAggregator agg = new LastValueAggregator(descriptor);
        InMemoryGauge       g   = new InMemoryGauge(descriptor, agg);
        g.record(0.5);
        agg.collectMetric();
        assert !agg.collectMetric();
    }
}
