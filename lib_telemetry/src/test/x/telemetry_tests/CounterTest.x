import telemetry.metrics.model.AggregationTemporality;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.Metric;
import telemetry.metrics.model.NumberDataPoint;
import telemetry.metrics.model.SumData;
import telemetry.metrics.memory.InMemoryCounter;
import telemetry.metrics.memory.SumAggregator;

class CounterTest {

    static InstrumentDescriptor descriptor =
        new InstrumentDescriptor("test.requests", unit = "{request}", description = "Test counter");

    // ----- delta mode ----------------------------------------------------------------------------

    @Test
    void deltaReturnsNullWhenNothingRecorded() {
        SumAggregator agg = new SumAggregator(descriptor, True, AggregationTemporality.Delta);
        assert !agg.collectMetric();
    }

    @Test
    void deltaSumsValuesForSameAttributeSet() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Delta);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(3.0);
        c.add(5.0);

        assert Metric m := agg.collectMetric();
        assert m.name == "test.requests";
        assert m.data.is(SumData);
        SumData data = m.data.as(SumData);
        assert data.isMonotonic;
        assert data.aggregationTemporality == AggregationTemporality.Delta;
        assert data.dataPoints.size == 1;
        assert data.dataPoints[0].value.as(Float64) == 8.0;
    }

    @Test
    void deltaProducesSeparateDataPointsPerAttributeSet() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Delta);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(3.0, ["region" = "us-east"]);
        c.add(5.0, ["region" = "eu-west"]);

        assert Metric m := agg.collectMetric();
        SumData data = m.data.as(SumData);
        assert data.dataPoints.size == 2;
        Float64 total = data.dataPoints[0].value.as(Float64) + data.dataPoints[1].value.as(Float64);
        assert total == 8.0;
    }

    @Test
    void deltaDataPointCarriesCorrectValue() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Delta);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(7.0);

        assert Metric m := agg.collectMetric();
        NumberDataPoint p = m.data.as(SumData).dataPoints[0];
        assert p.value.is(Float64);
        assert p.value.as(Float64) == 7.0;
    }

    @Test
    void deltaResetsAfterCollect() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Delta);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(1.0);
        agg.collectMetric();
        assert !agg.collectMetric();
    }

    @Test
    void deltaStartTimeAdvancesAfterCollect() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Delta);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(1.0);
        assert Metric m1 := agg.collectMetric();
        UInt64? start1 = m1.data.as(SumData).dataPoints[0].startTimeUnixNano;

        c.add(2.0);
        assert Metric m2 := agg.collectMetric();
        UInt64? start2 = m2.data.as(SumData).dataPoints[0].startTimeUnixNano;

        assert start1 != Null && start2 != Null;
        assert start2.as(UInt64) >= start1.as(UInt64);
    }

    // ----- cumulative mode -----------------------------------------------------------------------

    @Test
    void cumulativeReturnsNullBeforeFirstAdd() {
        SumAggregator agg = new SumAggregator(descriptor, True, AggregationTemporality.Cumulative);
        assert !agg.collectMetric();
    }

    @Test
    void cumulativeAccumulatesRunningTotal() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Cumulative);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(3.0);

        assert Metric m1 := agg.collectMetric();
        SumData d1 = m1.data.as(SumData);
        assert d1.aggregationTemporality == AggregationTemporality.Cumulative;
        assert d1.dataPoints.size == 1;
        assert d1.dataPoints[0].value.as(Float64) == 3.0;

        c.add(5.0);

        assert Metric m2 := agg.collectMetric();
        // Running total: 3 + 5 = 8
        assert m2.data.as(SumData).dataPoints[0].value.as(Float64) == 8.0;
    }

    @Test
    void cumulativeStartTimeIsFixed() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Cumulative);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(1.0);
        assert Metric m1 := agg.collectMetric();
        UInt64? start1 = m1.data.as(SumData).dataPoints[0].startTimeUnixNano;

        c.add(1.0);
        assert Metric m2 := agg.collectMetric();
        UInt64? start2 = m2.data.as(SumData).dataPoints[0].startTimeUnixNano;

        assert start1 == start2 as "startTimeNanos must not change in cumulative mode";
    }

    @Test
    void cumulativeProducesIsMonotonicSumData() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Cumulative);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(1.0);
        assert Metric m := agg.collectMetric();
        assert m.data.as(SumData).isMonotonic;
    }

    @Test
    void cumulativeTracksPerAttributeSetRunningTotals() {
        SumAggregator   agg = new SumAggregator(descriptor, True, AggregationTemporality.Cumulative);
        InMemoryCounter c   = new InMemoryCounter(descriptor, agg);
        c.add(1.0, ["region" = "us-east"]);
        c.add(2.0, ["region" = "eu-west"]);

        assert Metric m1 := agg.collectMetric();
        assert m1.data.as(SumData).dataPoints.size == 2;

        c.add(10.0, ["region" = "us-east"]);
        assert Metric m2 := agg.collectMetric();
        SumData d2 = m2.data.as(SumData);
        assert d2.dataPoints.size == 2;
        // us-east running total: 1 + 10 = 11; eu-west: 2
        Float64 usEast = 0.0;
        Float64 euWest = 0.0;
        for (NumberDataPoint p : d2.dataPoints) {
            if (p.attributes["region"] == "us-east") { usEast = p.value.as(Float64); }
            if (p.attributes["region"] == "eu-west") { euWest = p.value.as(Float64); }
        }
        assert usEast == 11.0;
        assert euWest == 2.0;
    }
}
