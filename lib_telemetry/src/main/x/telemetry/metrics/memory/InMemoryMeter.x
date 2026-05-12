import ecstasy.maps.HashMap;

import metrics.model.AggregationTemporality;
import metrics.model.Metric;
import metrics.model.ScopeMetrics;

/**
 * An in-memory [Meter] that creates real accumulating instruments and tracks them for
 * collection via [collect].
 *
 * Each instrument is backed by one [Aggregator] per matching [View]. The aggregator
 * determines the output type and handles storage; the instrument wrapper applies the
 * view's attribute filter and routes `add()`/`record()` calls to its aggregator(s).
 * Aggregators (not instrument wrappers) are registered in the collection list.
 *
 * Instrument deduplication: calling any factory method more than once with an equal
 * [InstrumentDescriptor] (same name and unit) of the same type returns the original
 * instance.
 */
service InMemoryMeter(InstrumentationScope   scope,
                      AggregationTemporality temporality = DefaultAggregationTemporality,
                      View[]                 views       = [])
        implements Meter, MetricDefaults {

    private Collectable[]                                      instruments              = new Array();
    private Map<InstrumentDescriptor, Counter>                 counters                 = new HashMap();
    private Map<InstrumentDescriptor, UpDownCounter>           upDownCounters           = new HashMap();
    private Map<InstrumentDescriptor, Histogram>               histograms               = new HashMap();
    private Map<InstrumentDescriptor, Gauge>                   gauges                   = new HashMap();
    private Map<InstrumentDescriptor, ObservableCounter>       observableCounters       = new HashMap();
    private Map<InstrumentDescriptor, ObservableUpDownCounter> observableUpDownCounters = new HashMap();
    private Map<InstrumentDescriptor, ObservableGauge>         observableGauges         = new HashMap();

    // ----- Meter interface -----------------------------------------------------------------------

    @Override
    Counter createCounter(InstrumentDescriptor descriptor) {
        Counter c;
        if (c := counters.get(descriptor)) { return c; }

        if (views.empty) {
            SumAggregator agg = new SumAggregator(descriptor, True, temporality);
            instruments.add(agg);
            c = new InMemoryCounter(descriptor, agg);
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.Counter);
            if (dropAll) {
                c = new noop.NoOpCounter(descriptor);
            } else if (streams.size == 1) {
                c = makeCounterWithStream(descriptor, streams[0]);
            } else {
                Counter[] delegates = [];
                for (StreamConfig cfg : streams) {
                    delegates += [makeCounterWithStream(descriptor, cfg)];
                }
                c = new FanOutCounter(descriptor, delegates);
            }
        }

        counters.put(descriptor, c);
        return c;
    }

    @Override
    UpDownCounter createUpDownCounter(InstrumentDescriptor descriptor) {
        UpDownCounter c;
        if (c := upDownCounters.get(descriptor)) { return c; }

        if (views.empty) {
            SumAggregator agg = new SumAggregator(descriptor, False, temporality);
            instruments.add(agg);
            c = new InMemoryUpDownCounter(descriptor, agg);
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.UpDownCounter);
            if (dropAll) {
                c = new noop.NoOpUpDownCounter(descriptor);
            } else if (streams.size == 1) {
                c = makeUpDownCounterWithStream(descriptor, streams[0]);
            } else {
                UpDownCounter[] delegates = [];
                for (StreamConfig cfg : streams) {
                    delegates += [makeUpDownCounterWithStream(descriptor, cfg)];
                }
                c = new FanOutUpDownCounter(descriptor, delegates);
            }
        }

        upDownCounters.put(descriptor, c);
        return c;
    }

    @Override
    Histogram createHistogram(InstrumentDescriptor descriptor,
                              Float64[]            bucketBoundaries = []) {
        Histogram h;
        if (h := histograms.get(descriptor)) { return h; }

        if (views.empty) {
            h = makeDefaultHistogram(descriptor, bucketBoundaries);
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.Histogram);
            if (dropAll) {
                h = new noop.NoOpHistogram(descriptor);
            } else if (streams.size == 1) {
                h = makeHistogramWithStream(descriptor, streams[0], bucketBoundaries);
            } else {
                Histogram[] delegates = [];
                for (StreamConfig cfg : streams) {
                    delegates += [makeHistogramWithStream(descriptor, cfg, bucketBoundaries)];
                }
                h = new FanOutHistogram(descriptor, delegates, bucketBoundaries);
            }
        }

        histograms.put(descriptor, h);
        return h;
    }

    @Override
    Gauge createGauge(InstrumentDescriptor descriptor) {
        Gauge g;
        if (g := gauges.get(descriptor)) { return g; }

        if (views.empty) {
            LastValueAggregator agg = new LastValueAggregator(descriptor);
            instruments.add(agg);
            g = new InMemoryGauge(descriptor, agg);
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.Gauge);
            if (dropAll) {
                g = new noop.NoOpGauge(descriptor);
            } else if (streams.size == 1) {
                g = makeGaugeWithStream(descriptor, streams[0]);
            } else {
                Gauge[] delegates = [];
                for (StreamConfig cfg : streams) {
                    delegates += [makeGaugeWithStream(descriptor, cfg)];
                }
                g = new FanOutGauge(descriptor, delegates);
            }
        }

        gauges.put(descriptor, g);
        return g;
    }

    @Override
    ObservableCounter createObservableCounter(InstrumentDescriptor descriptor) {
        ObservableCounter c;
        if (c := observableCounters.get(descriptor)) { return c; }

        if (views.empty) {
            InMemoryObservableCounter acc = new InMemoryObservableCounter(descriptor);
            instruments.add(acc);
            c = acc;
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.ObservableCounter);
            if (dropAll) {
                c = new noop.NoOpObservableCounter(descriptor);
            } else if (streams.size == 1) {
                InMemoryObservableCounter acc = new InMemoryObservableCounter(
                    effectiveDescriptor(descriptor, streams[0]),
                    streams[0].hasFilter ? streams[0] : Null);
                instruments.add(acc);
                c = acc;
            } else {
                InMemoryObservableCounter[] delegates = [];
                for (StreamConfig cfg : streams) {
                    InMemoryObservableCounter acc = new InMemoryObservableCounter(
                        effectiveDescriptor(descriptor, cfg),
                        cfg.hasFilter ? cfg : Null);
                    instruments.add(acc);
                    delegates   += [acc];
                }
                c = new FanOutObservableCounter(descriptor, delegates);
            }
        }

        observableCounters.put(descriptor, c);
        return c;
    }

    @Override
    ObservableUpDownCounter createObservableUpDownCounter(InstrumentDescriptor descriptor) {
        ObservableUpDownCounter c;
        if (c := observableUpDownCounters.get(descriptor)) { return c; }

        if (views.empty) {
            InMemoryObservableUpDownCounter acc = new InMemoryObservableUpDownCounter(descriptor);
            instruments.add(acc);
            c = acc;
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.ObservableUpDownCounter);
            if (dropAll) {
                c = new noop.NoOpObservableUpDownCounter(descriptor);
            } else if (streams.size == 1) {
                InMemoryObservableUpDownCounter acc = new InMemoryObservableUpDownCounter(
                    effectiveDescriptor(descriptor, streams[0]),
                    streams[0].hasFilter ? streams[0] : Null);
                instruments.add(acc);
                c = acc;
            } else {
                InMemoryObservableUpDownCounter[] delegates = [];
                for (StreamConfig cfg : streams) {
                    InMemoryObservableUpDownCounter acc = new InMemoryObservableUpDownCounter(
                        effectiveDescriptor(descriptor, cfg),
                        cfg.hasFilter ? cfg : Null);
                    instruments.add(acc);
                    delegates   += [acc];
                }
                c = new FanOutObservableUpDownCounter(descriptor, delegates);
            }
        }

        observableUpDownCounters.put(descriptor, c);
        return c;
    }

    @Override
    ObservableGauge createObservableGauge(InstrumentDescriptor descriptor) {
        ObservableGauge g;
        if (g := observableGauges.get(descriptor)) { return g; }

        if (views.empty) {
            InMemoryObservableGauge acc = new InMemoryObservableGauge(descriptor);
            instruments.add(acc);
            g = acc;
        } else {
            (StreamConfig[] streams, Boolean dropAll) = resolveStreams(descriptor, InstrumentType.ObservableGauge);
            if (dropAll) {
                g = new noop.NoOpObservableGauge(descriptor);
            } else if (streams.size == 1) {
                InMemoryObservableGauge acc = new InMemoryObservableGauge(
                    effectiveDescriptor(descriptor, streams[0]),
                    streams[0].hasFilter ? streams[0] : Null);
                instruments.add(acc);
                g = acc;
            } else {
                InMemoryObservableGauge[] delegates = [];
                for (StreamConfig cfg : streams) {
                    InMemoryObservableGauge acc = new InMemoryObservableGauge(
                        effectiveDescriptor(descriptor, cfg),
                        cfg.hasFilter ? cfg : Null);
                    instruments.add(acc);
                    delegates   += [acc];
                }
                g = new FanOutObservableGauge(descriptor, delegates);
            }
        }

        observableGauges.put(descriptor, g);
        return g;
    }

    // ----- collection ----------------------------------------------------------------------------

    ScopeMetrics collect() {
        Metric[] metrics = new Array();
        for (Collectable c : instruments) {
            if (Metric m := c.collectMetric()) {
                metrics.add(m);
            }
        }
        return new ScopeMetrics(scope, metrics);
    }

    // ----- instrument factory helpers ------------------------------------------------------------

    private Counter makeCounterWithStream(InstrumentDescriptor desc, StreamConfig cfg) {
        Aggregator agg = makeAggregator(effectiveDescriptor(desc, cfg),
                                        cfg.aggregation, InstrumentType.Counter);
        instruments.add(agg);
        return new InMemoryCounter(desc, agg, cfg.hasFilter ? cfg : Null);
    }

    private UpDownCounter makeUpDownCounterWithStream(InstrumentDescriptor desc, StreamConfig cfg) {
        Aggregator agg = makeAggregator(effectiveDescriptor(desc, cfg),
                                        cfg.aggregation, InstrumentType.UpDownCounter);
        instruments.add(agg);
        return new InMemoryUpDownCounter(desc, agg, cfg.hasFilter ? cfg : Null);
    }

    private Gauge makeGaugeWithStream(InstrumentDescriptor desc, StreamConfig cfg) {
        Aggregator agg = makeAggregator(effectiveDescriptor(desc, cfg),
                                        cfg.aggregation, InstrumentType.Gauge);
        instruments.add(agg);
        return new InMemoryGauge(desc, agg, cfg.hasFilter ? cfg : Null);
    }

    private Histogram makeDefaultHistogram(InstrumentDescriptor desc, Float64[] bucketBoundaries) {
        ExplicitBucketHistogramAggregator agg = new ExplicitBucketHistogramAggregator(
            desc, bucketBoundaries, temporality);
        instruments.add(agg);
        return new InMemoryHistogram(desc, agg, bucketBoundaries);
    }

    private Histogram makeHistogramWithStream(InstrumentDescriptor desc,
                                              StreamConfig         cfg,
                                              Float64[]            bucketBoundaries) {
        InstrumentDescriptor effective = effectiveDescriptor(desc, cfg);
        Aggregation viewAgg = cfg.aggregation;
        if (viewAgg.is(Aggregation.DefaultAggregation)) {
            return makeDefaultHistogram(effective, bucketBoundaries);
        }
        Aggregator agg = makeAggregator(effective, viewAgg, InstrumentType.Histogram);
        instruments.add(agg);
        Float64[] bounds = viewAgg.is(Aggregation.ExplicitBucketHistogramAggregation)
            ? viewAgg.as(Aggregation.ExplicitBucketHistogramAggregation).boundaries
            : [];
        return new InMemoryHistogram(desc, agg, bounds);
    }

    // ----- aggregator factory --------------------------------------------------------------------

    /**
     * Creates the appropriate [Aggregator] for the given descriptor, aggregation config,
     * and instrument type. Falls back to the instrument's default aggregation when the
     * config is [Aggregation.Default].
     */
    private Aggregator makeAggregator(InstrumentDescriptor desc,
                                       Aggregation          agg,
                                       InstrumentType       iType) {
        if (agg.is(Aggregation.SumAggregation)) {
            Boolean isMonotonic = iType == InstrumentType.Counter || iType == InstrumentType.Histogram;
            return new SumAggregator(desc, isMonotonic, temporality);
        }
        if (agg.is(Aggregation.LastValueAggregation)) {
            return new LastValueAggregator(desc);
        }
        if (agg.is(Aggregation.ExplicitBucketHistogramAggregation)) {
            var h = agg.as(Aggregation.ExplicitBucketHistogramAggregation);
            return new ExplicitBucketHistogramAggregator(desc, h.boundaries, temporality, h.recordMinMax);
        }
        if (agg.is(Aggregation.Base2ExponentialHistogramAggregation)) {
            var e = agg.as(Aggregation.Base2ExponentialHistogramAggregation);
            return new Base2ExponentialHistogramAggregator(desc, e.maxSize, e.maxScale,
                                                           temporality, e.recordMinMax);
        }
        // Default: instrument's natural aggregation
        return switch (iType) {
            case InstrumentType.Counter:
                new SumAggregator(desc, True, temporality);
            case InstrumentType.UpDownCounter:
                new SumAggregator(desc, False, temporality);
            case InstrumentType.Gauge:
                new LastValueAggregator(desc);
            default:
                // Histogram defaults handled in makeHistogramWithStream
                new ExplicitBucketHistogramAggregator(desc, [], temporality);
        };
    }

    // ----- view helpers --------------------------------------------------------------------------

    private (StreamConfig[], Boolean) resolveStreams(InstrumentDescriptor desc, InstrumentType iType) {
        StreamConfig[] matched  = [];
        Boolean        anyMatch = False;
        for (View v : views) {
            if (v.selector.matches(desc, iType, scope)) {
                anyMatch = True;
                if (!v.config.aggregation.isDrop) {
                    matched += [v.config];
                }
            }
        }
        if (matched.empty) {
            if (anyMatch) {
                return ([], True);
            }
            return ([new StreamConfig()], False);
        }
        return (matched, False);
    }

    private static InstrumentDescriptor effectiveDescriptor(InstrumentDescriptor desc, StreamConfig cfg) {
        if (cfg.name == Null && cfg.description == Null) {
            return desc;
        }
        return new InstrumentDescriptor(cfg.name ?: desc.name, desc.unit, cfg.description ?: desc.description);
    }
}
