import telemetry.Resource;
import telemetry.metrics.InstrumentDescriptor;
import telemetry.metrics.model.ResourceMetrics;
import telemetry.metrics.export.MetricExporter;
import telemetry.metrics.export.PeriodicExportingMetricReader;
import telemetry.metrics.memory.InMemoryMeterProvider;

class MetricReaderTest {

    static Resource testResource = new Resource([Resource.ServiceNameKey = "reader-test"]);

    // ----- test double ---------------------------------------------------------------------------

    /**
     * A MetricExporter that records every batch it receives, for assertion in tests.
     */
    static service RecordingExporter
            implements MetricExporter {

        ResourceMetrics[][] batches     = [];
        Int                 flushCount  = 0;
        Boolean             isShutdown  = False;

        @Override
        Result export(ResourceMetrics[] metrics) {
            if (isShutdown) {
                return DropData;
            }
            batches += [metrics];
            return Success;
        }

        @Override
        void forceFlush() {
            flushCount++;
        }

        @Override
        void shutdown() {
            isShutdown = True;
        }
    }

    // ----- forceFlush ----------------------------------------------------------------------------

    @Test
    void forceFlushDeliversBatchImmediately() {
        RecordingExporter     rec      = new RecordingExporter();
        PeriodicExportingMetricReader reader   =
            new PeriodicExportingMetricReader(rec, Duration.ofSeconds(60));
        InMemoryMeterProvider provider =
            new InMemoryMeterProvider(resource = testResource, readers = [reader]);

        provider.getMeter("test")
                .createCounter(new InstrumentDescriptor("hits", unit = "{hit}"))
                .add(5.0);

        provider.forceFlush();

        assert rec.batches.size == 1 as "forceFlush must deliver exactly one batch";
        assert rec.flushCount  == 1 as "forceFlush must call exporter forceFlush";
    }

    @Test
    void forceFlushAfterShutdownIsNoop() {
        RecordingExporter             rec    = new RecordingExporter();
        PeriodicExportingMetricReader reader =
            new PeriodicExportingMetricReader(rec, Duration.ofSeconds(60));
        InMemoryMeterProvider         provider =
            new InMemoryMeterProvider(resource = testResource, readers = [reader]);

        provider.getMeter("test")
                .createCounter(new InstrumentDescriptor("hits", unit = "{hit}"))
                .add(1.0);

        provider.shutdown();
        provider.forceFlush();

        assert rec.batches.size == 0 as "forceFlush after shutdown must not export";
    }

    // ----- shutdown ------------------------------------------------------------------------------

    @Test
    void shutdownShutsDownExporter() {
        RecordingExporter             rec    = new RecordingExporter();
        PeriodicExportingMetricReader reader =
            new PeriodicExportingMetricReader(rec, Duration.ofSeconds(60));
        InMemoryMeterProvider provider =
            new InMemoryMeterProvider(resource = testResource, readers = [reader]);

        provider.shutdown();

        assert rec.isShutdown as "shutdown must shut down the underlying exporter";
    }

    @Test
    void collectAfterShutdownIsNoop() {
        RecordingExporter             rec    = new RecordingExporter();
        PeriodicExportingMetricReader reader =
            new PeriodicExportingMetricReader(rec, Duration.ofSeconds(60));
        InMemoryMeterProvider provider =
            new InMemoryMeterProvider(resource = testResource, readers = [reader]);

        provider.getMeter("test")
                .createCounter(new InstrumentDescriptor("hits", unit = "{hit}"))
                .add(3.0);

        provider.shutdown();
        reader.collect();

        assert rec.batches.size == 0 as "collect after shutdown must not export";
    }

    // ----- multiple readers ----------------------------------------------------------------------

    @Test
    void multipleReadersBothReceiveBatch() {
        RecordingExporter rec1 = new RecordingExporter();
        RecordingExporter rec2 = new RecordingExporter();
        InMemoryMeterProvider provider = new InMemoryMeterProvider(
            resource = testResource,
            readers  = [
                new PeriodicExportingMetricReader(rec1, Duration.ofSeconds(60)),
                new PeriodicExportingMetricReader(rec2, Duration.ofSeconds(60)),
            ]);

        provider.getMeter("test")
                .createCounter(new InstrumentDescriptor("hits", unit = "{hit}"))
                .add(7.0);

        provider.forceFlush();

        assert rec1.batches.size == 1 as "First reader must receive the batch";
        assert rec2.batches.size == 1 as "Second reader must receive the batch";
    }

    // ----- periodic firing -----------------------------------------------------------------------

    @Test
    void periodicReaderFiresAfterInterval() {
        @Inject Clock clock;

        RecordingExporter             rec    = new RecordingExporter();
        PeriodicExportingMetricReader reader =
            new PeriodicExportingMetricReader(rec, Duration.ofMillis(50));
        InMemoryMeterProvider provider =
            new InMemoryMeterProvider(resource = testResource, readers = [reader]);

        provider.getMeter("test")
                .createCounter(new InstrumentDescriptor("hits", unit = "{hit}"))
                .add(2.0);

        // Wait ~3× the interval for the background callback to fire.
        @Future Boolean fired;
        clock.schedule(Duration.ofMillis(200), () -> { fired = True; });
        Boolean _ = fired;

        assert rec.batches.size >= 1 as "Periodic reader must have exported at least once";

        reader.shutdown();
    }

    // ----- direct collect (no reader) ------------------------------------------------------------

    @Test
    void providerWithNoReadersStillSupportsDirectCollect() {
        InMemoryMeterProvider provider = new InMemoryMeterProvider(resource = testResource);

        provider.getMeter("test")
                .createCounter(new InstrumentDescriptor("hits", unit = "{hit}"))
                .add(4.0);

        ResourceMetrics[] batch = provider.collect();
        assert batch.size == 1;
        assert !batch[0].scopeMetrics.empty;
    }
}
