import telemetry.metrics.model.AggregationTemporality;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.Metric;
import telemetry.metrics.model.SumData;
import telemetry.metrics.memory.InMemoryUpDownCounter;
import telemetry.metrics.memory.SumAggregator;

class UpDownCounterTest {

    static InstrumentDescriptor descriptor =
        new InstrumentDescriptor("test.queue.depth", unit = "{item}");

    @Test
    void deltaReturnsNullWhenNothingRecorded() {
        SumAggregator agg = new SumAggregator(descriptor, False, AggregationTemporality.Delta);
        assert !agg.collectMetric();
    }

    @Test
    void deltaAcceptsPositiveAndNegativeValues() {
        SumAggregator         agg = new SumAggregator(descriptor, False, AggregationTemporality.Delta);
        InMemoryUpDownCounter c   = new InMemoryUpDownCounter(descriptor, agg);
        c.add(10.0, ["queue" = "a"]);
        c.add(-4.0, ["queue" = "b"]);

        assert Metric m := agg.collectMetric();
        SumData data = m.data.as(SumData);
        assert !data.isMonotonic;
        assert data.aggregationTemporality == AggregationTemporality.Delta;
        assert data.dataPoints.size == 2;
    }

    @Test
    void deltaSumsValuesForSameAttributeSet() {
        SumAggregator         agg = new SumAggregator(descriptor, False, AggregationTemporality.Delta);
        InMemoryUpDownCounter c   = new InMemoryUpDownCounter(descriptor, agg);
        c.add(10.0);
        c.add(-4.0);

        assert Metric m := agg.collectMetric();
        SumData data = m.data.as(SumData);
        assert data.dataPoints.size == 1;
        assert data.dataPoints[0].value.as(Float64) == 6.0;
    }

    @Test
    void deltaResetsAfterCollect() {
        SumAggregator         agg = new SumAggregator(descriptor, False, AggregationTemporality.Delta);
        InMemoryUpDownCounter c   = new InMemoryUpDownCounter(descriptor, agg);
        c.add(5.0);
        agg.collectMetric();
        assert !agg.collectMetric();
    }

    @Test
    void cumulativeAccumulatesSignedValues() {
        SumAggregator         agg = new SumAggregator(descriptor, False, AggregationTemporality.Cumulative);
        InMemoryUpDownCounter c   = new InMemoryUpDownCounter(descriptor, agg);
        c.add(10.0);
        c.add(-3.0);

        assert Metric m1 := agg.collectMetric();
        assert m1.data.as(SumData).dataPoints[0].value.as(Float64) == 7.0;

        c.add(-2.0);

        assert Metric m2 := agg.collectMetric();
        // Running total: 10 - 3 - 2 = 5
        assert m2.data.as(SumData).dataPoints[0].value.as(Float64) == 5.0;
    }

    @Test
    void cumulativeProducesNonMonotonicSumData() {
        SumAggregator         agg = new SumAggregator(descriptor, False, AggregationTemporality.Cumulative);
        InMemoryUpDownCounter c   = new InMemoryUpDownCounter(descriptor, agg);
        c.add(1.0);
        assert Metric m := agg.collectMetric();
        assert !m.data.as(SumData).isMonotonic;
    }
}
