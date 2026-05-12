import telemetry.metrics.model.AggregationTemporality;
import telemetry.metrics.model.HistogramData;
import telemetry.metrics.model.HistogramDataPoint;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.Metric;
import telemetry.metrics.memory.ExplicitBucketHistogramAggregator;
import telemetry.metrics.memory.InMemoryHistogram;

class HistogramTest {

    static InstrumentDescriptor descriptor =
        new InstrumentDescriptor("test.latency", unit = "ms");

    static Float64[] bounds = [5.0, 10.0, 50.0];

    // ----- basic recording -----------------------------------------------------------------------

    @Test
    void returnsNullWhenNothingRecorded() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Delta);
        assert !agg.collectMetric();
    }

    @Test
    void recordsCountAndSum() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(3.0);
        h.record(7.0);
        h.record(20.0);

        assert Metric m := agg.collectMetric();
        assert m.name == "test.latency";
        assert m.data.is(HistogramData);

        HistogramDataPoint p = m.data.as(HistogramData).dataPoints[0];
        assert p.count == 3;
        assert p.sum != Null && p.sum.as(Float64) == 30.0;
    }

    @Test
    void tracksMinAndMax() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(1.0);
        h.record(100.0);
        h.record(42.0);

        assert Metric mResult := agg.collectMetric();
        HistogramDataPoint p = mResult.data.as(HistogramData).dataPoints[0];
        assert p.min != Null && p.min.as(Float64) == 1.0;
        assert p.max != Null && p.max.as(Float64) == 100.0;
    }

    @Test
    void assignsValuesToBuckets() {
        // bounds = [5.0, 10.0, 50.0] → 4 buckets: (−∞,5], (5,10], (10,50], (50,+∞)
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(3.0);   // bucket 0: value <= 5.0
        h.record(7.0);   // bucket 1: 5.0 < value <= 10.0
        h.record(25.0);  // bucket 2: 10.0 < value <= 50.0
        h.record(100.0); // bucket 3: overflow (value > 50.0)

        assert Metric mResult := agg.collectMetric();
        HistogramDataPoint p = mResult.data.as(HistogramData).dataPoints[0];
        assert p.bucketCounts.size == 4;
        assert p.bucketCounts[0] == 1;
        assert p.bucketCounts[1] == 1;
        assert p.bucketCounts[2] == 1;
        assert p.bucketCounts[3] == 1;
    }

    @Test
    void boundaryValueGoesIntoLowerBucket() {
        // A value exactly equal to a bound goes into the bucket whose upper bound is that value
        Float64[] localBounds = [10.0, 20.0];
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, localBounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, localBounds);
        h.record(10.0); // value == 10.0 → bucket 0 (<=10.0)
        h.record(20.0); // value == 20.0 → bucket 1 (<=20.0)
        h.record(30.0); // overflow     → bucket 2

        assert Metric mResult := agg.collectMetric();
        HistogramDataPoint p = mResult.data.as(HistogramData).dataPoints[0];
        assert p.bucketCounts[0] == 1;
        assert p.bucketCounts[1] == 1;
        assert p.bucketCounts[2] == 1;
    }

    @Test
    void emptyBoundsProduceSingleOverflowBucket() {
        Float64[] emptyBounds = [];
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, emptyBounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, emptyBounds);
        h.record(42.0);

        assert Metric mResult := agg.collectMetric();
        HistogramDataPoint p = mResult.data.as(HistogramData).dataPoints[0];
        assert p.bucketCounts.size == 1;
        assert p.bucketCounts[0] == 1;
    }

    @Test
    void producesCorrectTemporality() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(1.0);
        assert Metric m := agg.collectMetric();
        assert m.data.as(HistogramData).aggregationTemporality == AggregationTemporality.Delta;
    }

    // ----- delta mode ----------------------------------------------------------------------------

    @Test
    void deltaResetsAfterCollect() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(5.0);
        agg.collectMetric();
        assert !agg.collectMetric();
    }

    @Test
    void deltaAccumulatesWithinOneCycle() {
        Float64[] localBounds = [10.0];
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, localBounds, AggregationTemporality.Delta);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, localBounds);
        h.record(3.0);
        h.record(3.0);

        assert Metric mResult := agg.collectMetric();
        HistogramDataPoint p = mResult.data.as(HistogramData).dataPoints[0];
        assert p.count == 2;
        assert p.bucketCounts[0] == 2; // both values <= 10.0
    }

    // ----- cumulative mode -----------------------------------------------------------------------

    @Test
    void cumulativeAccumulatesAcrossCollects() {
        Float64[] localBounds = [10.0];
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, localBounds, AggregationTemporality.Cumulative);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, localBounds);
        h.record(3.0);
        h.record(7.0);

        assert Metric m1 := agg.collectMetric();
        HistogramDataPoint p1 = m1.data.as(HistogramData).dataPoints[0];
        assert p1.count == 2;
        assert p1.sum.as(Float64) == 10.0;

        h.record(5.0);

        assert Metric m2 := agg.collectMetric();
        HistogramDataPoint p2 = m2.data.as(HistogramData).dataPoints[0];
        // State accumulates: count = 2+1 = 3, sum = 10+5 = 15
        assert p2.count == 3;
        assert p2.sum.as(Float64) == 15.0;
    }

    @Test
    void cumulativeStartTimeIsFixed() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Cumulative);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(1.0);
        assert Metric m1 := agg.collectMetric();
        UInt64? start1 = m1.data.as(HistogramData).dataPoints[0].startTimeUnixNano;

        h.record(2.0);
        assert Metric m2 := agg.collectMetric();
        UInt64? start2 = m2.data.as(HistogramData).dataPoints[0].startTimeUnixNano;

        assert start1 == start2 as "startTimeNanos must not change in cumulative mode";
    }

    @Test
    void cumulativeProducesCorrectTemporality() {
        ExplicitBucketHistogramAggregator agg =
            new ExplicitBucketHistogramAggregator(descriptor, bounds, AggregationTemporality.Cumulative);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, bounds);
        h.record(1.0);
        assert Metric m := agg.collectMetric();
        assert m.data.as(HistogramData).aggregationTemporality == AggregationTemporality.Cumulative;
    }
}
