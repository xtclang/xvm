import metrics.model.ExponentialHistogramData;
import metrics.model.ExponentialHistogramDataPoint;
import metrics.model.GaugeData;
import metrics.model.HistogramData;
import metrics.model.HistogramDataPoint;
import metrics.model.Metric;
import metrics.model.MetricData;
import metrics.model.NumberDataPoint;
import metrics.model.ResourceMetrics;
import metrics.model.ScopeMetrics;
import metrics.model.SumData;
import metrics.model.SummaryData;
import metrics.model.SummaryDataPoint;

/**
 * A [MetricExporter] that writes human-readable metric output to the console.
 *
 * Useful during development and debugging when no backend is available. Each call to
 * [export] prints one [ResourceMetrics] batch.
 */
service ConsoleExporter
        implements MetricExporter {

    @Inject Console console;

    private Boolean active = True;

    @Override
    Result export(ResourceMetrics[] metrics) {
        if (!active) {
            return DropData;
        }
        for (ResourceMetrics rm : metrics) {
            printResourceMetrics(rm);
        }
        return Success;
    }

    @Override
    void forceFlush() {}

    @Override
    void shutdown() {
        active = False;
    }

    // ----- formatting ----------------------------------------------------------------------------

    private void printResourceMetrics(ResourceMetrics rm) {
        console.print("--- ResourceMetrics ---");
        for ((String k, AnyValue v) : rm.resource.attributes) {
            console.print($"  resource.{k} = {v}");
        }
        for (ScopeMetrics sm : rm.scopeMetrics) {
            console.print($"  scope: {sm.scope.name}");
            for (Metric m : sm.metrics) {
                printMetric(m, "    ");
            }
        }
    }

    private void printMetric(Metric m, String indent) {
        console.print($"{indent}metric: {m.name} [{m.unit}]");
        MetricData data = m.data;
        if (data.is(GaugeData)) {
            for (NumberDataPoint p : data.dataPoints) {
                console.print($"{indent}  gauge  value={p.value}  attrs={p.attributes}");
            }
        } else if (data.is(SumData)) {
            console.print($|{indent}  sum  monotonic={data.isMonotonic} \
                           |temporality={data.aggregationTemporality}
                           );
            for (NumberDataPoint p : data.dataPoints) {
                console.print($"{indent}    value={p.value}  attrs={p.attributes}");
            }
        } else if (data.is(HistogramData)) {
            for (HistogramDataPoint p : data.dataPoints) {
                console.print($|{indent}  histogram  count={p.count}  sum={p.sum}  min={p.min}  \
                               |max={p.max}
                               );
            }
        } else if (data.is(ExponentialHistogramData)) {
            for (ExponentialHistogramDataPoint p : data.dataPoints) {
                console.print($|{indent}  exp-histogram  count={p.count}  sum={p.sum}  min={p.min} \
                               | max={p.max}  scale={p.scale}
                               );
            }
        } else if (data.is(SummaryData)) {
            for (SummaryDataPoint p : data.dataPoints) {
                console.print($"{indent}  summary  count={p.count}  sum={p.sum}");
            }
        }
    }
}
