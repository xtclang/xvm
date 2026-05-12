import telemetry.metrics.model.AggregationTemporality;
import telemetry.metrics.model.ExponentialHistogramData;
import telemetry.metrics.model.ExponentialHistogramDataPoint;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.Metric;
import telemetry.metrics.memory.Base2ExponentialHistogramAggregator;
import telemetry.metrics.memory.InMemoryHistogram;

class ExponentialHistogramTest {

    static InstrumentDescriptor descriptor =
        new InstrumentDescriptor("test.request.size", unit = "By");

    // ----- basic recording -----------------------------------------------------------------------

    @Test
    void returnsNullWhenNothingRecorded() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        assert !agg.collectMetric();
    }

    @Test
    void checkSize() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(0.159209);
        h.record(0.136458);
        h.record(0.103833);
        h.record(0.093042);
        h.record(0.09625);
        h.record(0.0805);
        h.record(0.113959);
        h.record(0.111166);
        h.record(0.093542);
        h.record(0.09275);

        assert Metric m := agg.collectMetric();
        assert m.data.is(ExponentialHistogramData);
        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        // Scale reduction is span-based: the dense BucketSet array must fit within maxBuckets.
        // All 10 values are recorded; the bucket span must not be millions (the bug this tests).
        assert p.count == 10;
        assert p.positive.counts.size <= 160 as "Bucket span must not exceed maxBuckets";
    }

    @Test
    void recordsCountAndSum() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(100.0);
        h.record(200.0);
        h.record(300.0);

        assert Metric m := agg.collectMetric();
        assert m.name == "test.request.size";
        assert m.data.is(ExponentialHistogramData);

        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        assert p.count == 3;
        assert p.sum != Null && p.sum.as(Float64) == 600.0;
    }

    @Test
    void tracksMinAndMax() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(10.0);
        h.record(500.0);
        h.record(50.0);

        assert Metric m := agg.collectMetric();


        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        assert p.min != Null && p.min.as(Float64) == 10.0;
        assert p.max != Null && p.max.as(Float64) == 500.0;
    }

    @Test
    void positiveValuesGoIntoPositiveBuckets() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(1.0);
        h.record(2.0);
        h.record(4.0);

        assert Metric m := agg.collectMetric();


        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        assert !p.positive.counts.empty as "Positive values must produce positive bucket entries";
        assert p.negative.counts.empty  as "No negative values recorded";
    }

    @Test
    void negativeValuesGoIntoNegativeBuckets() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(-1.0);
        h.record(-2.0);

        assert Metric m := agg.collectMetric();


        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        assert !p.negative.counts.empty as "Negative values must produce negative bucket entries";
        assert p.positive.counts.empty  as "No positive values recorded";
    }

    @Test
    void zeroValueGoesIntoZeroCount() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(0.0);

        assert Metric m := agg.collectMetric();


        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        assert p.zeroCount == 1;
        assert p.positive.counts.empty;
        assert p.negative.counts.empty;
    }

    @Test
    void producesCorrectTemporality() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(1.0);
        assert Metric m := agg.collectMetric();
        assert m.data.as(ExponentialHistogramData).aggregationTemporality
            == AggregationTemporality.Delta;
    }

    // ----- delta mode ----------------------------------------------------------------------------

    @Test
    void deltaResetsAfterCollect() {
        Base2ExponentialHistogramAggregator agg = new Base2ExponentialHistogramAggregator(descriptor);
        InMemoryHistogram                   h   = new InMemoryHistogram(descriptor, agg, []);
        h.record(1.0);
        agg.collectMetric();
        assert !agg.collectMetric();
    }

    // ----- cumulative mode -----------------------------------------------------------------------

    @Test
    void cumulativeAccumulatesAcrossCollects() {
        Base2ExponentialHistogramAggregator agg =
            new Base2ExponentialHistogramAggregator(descriptor,
                                                    temporality = AggregationTemporality.Cumulative);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, []);
        h.record(10.0);
        h.record(20.0);

        assert Metric m1 := agg.collectMetric();
        ExponentialHistogramDataPoint p1 =
            m1.data.as(ExponentialHistogramData).dataPoints[0];
        assert p1.count == 2;
        assert p1.sum.as(Float64) == 30.0;

        h.record(30.0);

        assert Metric m2 := agg.collectMetric();
        ExponentialHistogramDataPoint p2 =
            m2.data.as(ExponentialHistogramData).dataPoints[0];
        assert p2.count == 3;
        assert p2.sum.as(Float64) == 60.0;
    }

    @Test
    void cumulativeStartTimeIsFixed() {
        Base2ExponentialHistogramAggregator agg =
            new Base2ExponentialHistogramAggregator(descriptor,
                                                    temporality = AggregationTemporality.Cumulative);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, []);
        h.record(1.0);
        assert Metric m1 := agg.collectMetric();
        UInt64? start1 =
            m1.data.as(ExponentialHistogramData).dataPoints[0].startTimeUnixNano;

        h.record(2.0);
        assert Metric m2 := agg.collectMetric();
        UInt64? start2 =
            m2.data.as(ExponentialHistogramData).dataPoints[0].startTimeUnixNano;

        assert start1 == start2 as "startTimeNanos must not change in cumulative mode";
    }

    // ----- scale reduction -----------------------------------------------------------------------

    @Test
    void scaleReducesWhenBucketsExceedMax() {
        Base2ExponentialHistogramAggregator agg =
            new Base2ExponentialHistogramAggregator(descriptor, maxBuckets = 2);
        InMemoryHistogram h = new InMemoryHistogram(descriptor, agg, []);
        h.record(1.0);
        h.record(1000.0);
        h.record(1000000.0);

        assert Metric m := agg.collectMetric();
        ExponentialHistogramDataPoint p = m.data.as(ExponentialHistogramData).dataPoints[0];
        assert p.scale < 20 as "Scale should have been reduced to fit within maxBuckets";
        assert p.count == 3;
    }
}
