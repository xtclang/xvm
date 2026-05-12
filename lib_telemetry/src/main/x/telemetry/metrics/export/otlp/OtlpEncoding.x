import convert.formats.Base64Format;

import metrics.model.AggregationTemporality;
import metrics.model.BucketSet;
import metrics.model.Exemplar;
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
import metrics.model.ValueAtQuantile;

/**
 * Pure, stateless helpers that convert the telemetry data model into `json.Doc` trees following the
 * OTLP/HTTP JSON encoding rules:
 *
 *  - Field names are lowerCamelCase (matching the proto3 JSON mapping).
 *  - 64-bit unsigned integers are decimal strings (e.g. `"1700000000000000000"`).
 *  - `traceId` and `spanId` are lowercase hex strings.
 *  - All other byte arrays are base64 strings.
 *  - Enum values are integers (`Delta = 1`, `Cumulative = 2`).
 *  - Empty repeated fields are omitted.
 *  - Optional fields absent in the data model are omitted.
 */
const OtlpEncoding {

    // ----- top-level request ---------------------------------------------------------------------

    /**
     * Encodes a metric export batch as an `ExportMetricsServiceRequest` JSON document
     * ready to POST to `/v1/metrics`.
     */
    static json.Doc encodeRequest(ResourceMetrics[] metrics) {
        json.JsonObject obj  = json.newObject();
        json.JsonArray  arr  = json.newArray();
        for (ResourceMetrics rm : metrics) {
            arr.add(encodeResourceMetrics(rm));
        }
        obj.put("resourceMetrics", arr);
        return obj;
    }

    // ----- resource / scope ----------------------------------------------------------------------

    /**
     * Encodes a [ResourceMetrics] container including its resource attributes and all
     * child [ScopeMetrics].
     */
    static json.Doc encodeResourceMetrics(ResourceMetrics rm) {
        json.JsonObject obj    = json.newObject();
        json.JsonArray  scopes = json.newArray();
        obj.put("resource", encodeResource(rm.resource));
        for (ScopeMetrics sm : rm.scopeMetrics) {
            scopes.add(encodeScopeMetrics(sm));
        }
        obj.put("scopeMetrics", scopes);
        if (String url ?= rm.schemaUrl) {
            obj.put("schemaUrl", url);
        }
        return obj;
    }

    /**
     * Encodes a [ScopeMetrics] container including the instrumentation scope identity
     * and all child [Metric]s.
     */
    static json.Doc encodeScopeMetrics(ScopeMetrics sm) {
        json.JsonObject obj     = json.newObject();
        json.JsonArray  metrics = json.newArray();
        obj.put("scope", encodeScope(sm.scope));
        for (Metric m : sm.metrics) {
            metrics.add(encodeMetric(m));
        }
        obj.put("metrics", metrics);
        if (String url ?= sm.schemaUrl) {
            obj.put("schemaUrl", url);
        }
        return obj;
    }

    private static json.Doc encodeResource(Resource r) {
        json.JsonObject obj = json.newObject();
        obj.put("attributes", encodeAttributes(r.attributes));
        return obj;
    }

    private static json.Doc encodeScope(InstrumentationScope s) {
        json.JsonObject obj = json.newObject();
        obj.put("name", s.name);
        if (String v ?= s.version) {
            obj.put("version", v);
        }
        if (!s.attributes.empty) {
            obj.put("attributes", encodeAttributes(s.attributes));
        }
        return obj;
    }

    // ----- metric --------------------------------------------------------------------------------

    /**
     * Encodes a [Metric] with its name, description, unit, and instrument-specific data
     * payload. The data field name (`"gauge"`, `"sum"`, `"histogram"`, etc.) is chosen
     * based on the runtime type of [Metric.data].
     */
    static json.Doc encodeMetric(Metric m) {
        json.JsonObject obj = json.newObject();
        obj.put("name", m.name);
        if (!m.description.empty) {
            obj.put("description", m.description);
        }
        if (!m.unit.empty) {
            obj.put("unit", m.unit);
        }
        MetricData data = m.data;
        if (data.is(GaugeData)) {
            obj.put("gauge", encodeGauge(data));
        } else if (data.is(SumData)) {
            obj.put("sum", encodeSum(data));
        } else if (data.is(HistogramData)) {
            obj.put("histogram", encodeHistogram(data));
        } else if (data.is(ExponentialHistogramData)) {
            obj.put("exponentialHistogram",
                encodeExponentialHistogram(data));
        } else if (data.is(SummaryData)) {
            obj.put("summary", encodeSummary(data));
        }
        return obj;
    }

    // ----- instrument data types -----------------------------------------------------------------

    /**
     * Encodes a [GaugeData] payload containing its data points.
     */
    static json.Doc encodeGauge(GaugeData g) {
        json.JsonObject obj = json.newObject();
        obj.put("dataPoints", encodeNumberDataPoints(g.dataPoints));
        return obj;
    }

    /**
     * Encodes a [SumData] payload including temporality and monotonicity flags.
     */
    static json.Doc encodeSum(SumData s) {
        json.JsonObject obj = json.newObject();
        obj.put("dataPoints", encodeNumberDataPoints(s.dataPoints));
        obj.put("aggregationTemporality", encodeTemporality(s.aggregationTemporality));
        obj.put("isMonotonic", s.isMonotonic);
        return obj;
    }

    /**
     * Encodes a [HistogramData] payload including temporality and all data points.
     */
    static json.Doc encodeHistogram(HistogramData h) {
        json.JsonObject obj = json.newObject();
        json.JsonArray  pts = json.newArray();
        for (HistogramDataPoint p : h.dataPoints) {
            pts.add(encodeHistogramDataPoint(p));
        }
        obj.put("dataPoints", pts);
        obj.put("aggregationTemporality", encodeTemporality(h.aggregationTemporality));
        return obj;
    }

    /**
     * Encodes an [ExponentialHistogramData] payload including temporality and all data
     * points.
     */
    static json.Doc encodeExponentialHistogram(ExponentialHistogramData h) {
        json.JsonObject obj = json.newObject();
        json.JsonArray  pts = json.newArray();
        for (ExponentialHistogramDataPoint p : h.dataPoints) {
            pts.add(encodeExponentialHistogramDataPoint(p));
        }
        obj.put("dataPoints", pts);
        obj.put("aggregationTemporality", encodeTemporality(h.aggregationTemporality));
        return obj;
    }

    /**
     * Encodes a [SummaryData] payload containing its data points.
     */
    static json.Doc encodeSummary(SummaryData s) {
        json.JsonObject obj = json.newObject();
        json.JsonArray  pts = json.newArray();
        for (SummaryDataPoint p : s.dataPoints) {
            pts.add(encodeSummaryDataPoint(p));
        }
        obj.put("dataPoints", pts);
        return obj;
    }

    // ----- data points ---------------------------------------------------------------------------

    /**
     * Encodes a [NumberDataPoint] with attributes, timestamps, the `asInt` or `asDouble`
     * value field, optional exemplars, and flags.
     */
    static json.Doc encodeNumberDataPoint(NumberDataPoint p) {
        json.JsonObject obj = json.newObject();
        obj.put("attributes", encodeAttributes(p.attributes));
        if (UInt64 st ?= p.startTimeUnixNano) {
            obj.put("startTimeUnixNano", encodeUInt64(st));
        }
        obj.put("timeUnixNano", encodeUInt64(p.timeUnixNano));
        putNumberValue(obj, p.value);
        if (!p.exemplars.empty) {
            obj.put("exemplars", encodeExemplars(p.exemplars));
        }
        obj.put("flags", p.flags.noRecordedValue ? 1 : 0);
        return obj;
    }

    /**
     * Encodes a [HistogramDataPoint] with attributes, timestamps, count, optional
     * sum/min/max, explicit bucket boundaries (as JSON numbers), bucket counts (as
     * decimal strings), optional exemplars, and flags.
     */
    static json.Doc encodeHistogramDataPoint(HistogramDataPoint p) {
        json.JsonObject obj = json.newObject();
        obj.put("attributes", encodeAttributes(p.attributes));
        if (UInt64 st ?= p.startTimeUnixNano) {
            obj.put("startTimeUnixNano", encodeUInt64(st));
        }
        obj.put("timeUnixNano", encodeUInt64(p.timeUnixNano));
        obj.put("count", encodeUInt64(p.count));
        if (Float64 s ?= p.sum) {
            obj.put("sum", s.toFPLiteral());
        }
        if (Float64 mn ?= p.min) {
            obj.put("min", mn.toFPLiteral());
        }
        if (Float64 mx ?= p.max) {
            obj.put("max", mx.toFPLiteral());
        }
        obj.put("explicitBounds", encodeFloatArray(p.explicitBounds));
        obj.put("bucketCounts", encodeUInt64Array(p.bucketCounts));
        if (!p.exemplars.empty) {
            obj.put("exemplars", encodeExemplars(p.exemplars));
        }
        obj.put("flags", p.flags.noRecordedValue ? 1 : 0);
        return obj;
    }

    /**
     * Encodes an [ExponentialHistogramDataPoint] with attributes, timestamps, count,
     * optional sum/min/max, scale, zero bucket, positive and negative [BucketSet]s,
     * optional exemplars, and flags.
     */
    static json.Doc encodeExponentialHistogramDataPoint(ExponentialHistogramDataPoint p) {
        json.JsonObject obj = json.newObject();
        obj.put("attributes", encodeAttributes(p.attributes));
        if (UInt64 st ?= p.startTimeUnixNano) {
            obj.put("startTimeUnixNano", encodeUInt64(st));
        }
        obj.put("timeUnixNano", encodeUInt64(p.timeUnixNano));
        obj.put("count", encodeUInt64(p.count));
        if (Float64 s ?= p.sum) {
            obj.put("sum", s.toFPLiteral());
        }
        if (Float64 mn ?= p.min) {
            obj.put("min", mn.toFPLiteral());
        }
        if (Float64 mx ?= p.max) {
            obj.put("max", mx.toFPLiteral());
        }
        obj.put("scale", p.scale.toIntLiteral());
        obj.put("zeroCount", encodeUInt64(p.zeroCount));
        obj.put("positive", encodeBucketSet(p.positive));
        obj.put("negative", encodeBucketSet(p.negative));
        if (p.zeroThreshold != 0.0) {
            obj.put("zeroThreshold", p.zeroThreshold.toFPLiteral());
        }
        if (!p.exemplars.empty) {
            obj.put("exemplars", encodeExemplars(p.exemplars));
        }
        obj.put("flags", p.flags.noRecordedValue ? 1 : 0);
        return obj;
    }

    /**
     * Encodes a [SummaryDataPoint] with attributes, timestamps, count, sum, optional
     * quantile values, and flags.
     */
    static json.Doc encodeSummaryDataPoint(SummaryDataPoint p) {
        json.JsonObject obj = json.newObject();
        obj.put("attributes", encodeAttributes(p.attributes));
        if (UInt64 st ?= p.startTimeUnixNano) {
            obj.put("startTimeUnixNano", encodeUInt64(st));
        }
        obj.put("timeUnixNano", encodeUInt64(p.timeUnixNano));
        obj.put("count", encodeUInt64(p.count));
        obj.put("sum", p.sum.toFPLiteral());
        if (!p.quantileValues.empty) {
            json.JsonArray qArr = json.newArray();
            for (ValueAtQuantile q : p.quantileValues) {
                json.JsonObject qObj = json.newObject();
                qObj.put("quantile", q.quantile.toFPLiteral());
                qObj.put("value", q.value.toFPLiteral());
                qArr.add(qObj);
            }
            obj.put("quantileValues", qArr);
        }
        obj.put("flags", p.flags.noRecordedValue ? 1 : 0);
        return obj;
    }

    // ----- exemplar ------------------------------------------------------------------------------

    /**
     * Encodes an [Exemplar] with optional filtered attributes, the observation timestamp,
     * the `asInt` or `asDouble` value, and optional `spanId` / `traceId` hex strings.
     */
    static json.Doc encodeExemplar(Exemplar e) {
        json.JsonObject obj = json.newObject();
        if (!e.filteredAttributes.empty) {
            obj.put("filteredAttributes", encodeAttributes(e.filteredAttributes));
        }
        obj.put("timeUnixNano", encodeUInt64(e.timeUnixNano));
        putNumberValue(obj, e.value);
        if (Byte[] spanId ?= e.spanId) {
            obj.put("spanId", encodeHex(spanId));
        }
        if (Byte[] traceId ?= e.traceId) {
            obj.put("traceId", encodeHex(traceId));
        }
        return obj;
    }

    // ----- attributes / AnyValue -----------------------------------------------------------------

    /**
     * Encodes an [AnyValue] as the appropriate OTLP JSON tagged-union object. The key
     * (`stringValue`, `boolValue`, `intValue`, `doubleValue`, `bytesValue`, `arrayValue`,
     * or `kvlistValue`) is chosen based on the runtime type of `value`. `Nullable` values
     * produce an empty object; callers are expected to filter these before encoding.
     */
    static json.Doc encodeAnyValue(AnyValue value) {
        json.JsonObject obj = json.newObject();
        if (value.is(String)) {
            obj.put("stringValue", value);
        } else if (value.is(Boolean)) {
            obj.put("boolValue", value);
        } else if (value.is(Int)) {
            obj.put("intValue", value.toString());
        } else if (value.is(IntLiteral)) {
            obj.put("intValue", value.toString());
        } else if (value.is(Float64)) {
            obj.put("doubleValue", value.toFPLiteral());
        } else if (value.is(FPLiteral)) {
            obj.put("doubleValue", value);
        } else if (value.is(Byte[])) {
            obj.put("bytesValue", Base64Format.Instance.encode(value));
        } else if (value.is(PrimitiveValue[])) {
            json.JsonObject inner = json.newObject();
            json.JsonArray  vals  = json.newArray();
            for (PrimitiveValue pv : value) {
                vals.add(encodeAnyValue(pv));
            }
            inner.put("values", vals);
            obj.put("arrayValue", inner);
        } else if (value.is(Map<String, PrimitiveValue>)) {
            json.JsonObject inner   = json.newObject();
            json.JsonArray  entries = json.newArray();
            for ((String k, PrimitiveValue v) : value) {
                json.JsonObject entry = json.newObject();
                entry.put("key", k);
                entry.put("value", encodeAnyValue(v));
                entries.add(entry);
            }
            inner.put("values", entries);
            obj.put("kvlistValue", inner);
        }
        return obj;
    }

    // ----- primitive helpers ---------------------------------------------------------------------

    /**
     * Encodes a `UInt64` as a decimal string, as required by OTLP for all 64-bit integer
     * fields (timestamps, counts, bucket counts, etc.).
     */
    static String encodeUInt64(UInt64 value) = value.toString();

    /**
     * Encodes a byte array as a lowercase hexadecimal string. Used for `traceId` (16
     * bytes → 32 hex chars) and `spanId` (8 bytes → 16 hex chars).
     */
    static String encodeHex(Byte[] bytes) {
        String       hex = "0123456789abcdef";
        StringBuffer buf = new StringBuffer(bytes.size * 2);
        for (Byte b : bytes) {
            buf.add(hex[(b >> 4).toInt()]);
            buf.add(hex[(b & 0x0F).toInt()]);
        }
        return buf.toString();
    }

    // ----- private helpers -----------------------------------------------------------------------

    /**
     * Encodes attributes as a JSON array of `KeyValue` objects, omitting null values.
     */
    private static json.Doc encodeAttributes(Map<String, AnyValue> attrs) {
        json.JsonArray arr = json.newArray();
        for ((String k, AnyValue v) : attrs) {
            if (!v.is(Nullable)) {
                json.JsonObject entry = json.newObject();
                entry.put("key", k);
                entry.put("value", encodeAnyValue(v));
                arr.add(entry);
            }
        }
        return arr;
    }

    /**
     * Encodes a sequence of [Exemplar]s as a JSON array.
     */
    private static json.Doc encodeExemplars(Exemplar[] exemplars) {
        json.JsonArray arr = json.newArray();
        for (Exemplar e : exemplars) {
            arr.add(encodeExemplar(e));
        }
        return arr;
    }

    /**
     * Encodes a sequence of [NumberDataPoint]s as a JSON array.
     */
    private static json.Doc encodeNumberDataPoints(NumberDataPoint[] points) {
        json.JsonArray arr = json.newArray();
        for (NumberDataPoint p : points) {
            arr.add(encodeNumberDataPoint(p));
        }
        return arr;
    }

    /**
     * Encodes a [BucketSet] as `{"offset": N, "bucketCounts": ["…", …]}`.
     */
    private static json.Doc encodeBucketSet(BucketSet bs) {
        json.JsonObject obj = json.newObject();
        obj.put("offset", bs.offset.toIntLiteral());
        obj.put("bucketCounts", encodeUInt64Array(bs.counts));
        return obj;
    }

    /**
     * Encodes a `Float64[]` as a JSON array of numbers (not strings).
     */
    private static json.Doc encodeFloatArray(Float64[] values) {
        json.JsonArray arr = json.newArray();
        for (Float64 v : values) {
            arr.add(v.toFPLiteral());
        }
        return arr;
    }

    /**
     * Encodes a `UInt64[]` as a JSON array of decimal strings.
     */
    private static json.Doc encodeUInt64Array(UInt64[] values) {
        json.JsonArray arr = json.newArray();
        for (UInt64 v : values) {
            arr.add(encodeUInt64(v));
        }
        return arr;
    }

    /**
     * Places either `"asDouble"` (JSON number) or `"asInt"` (decimal string) into `obj`
     * depending on the runtime type of `value`.
     */
    private static void putNumberValue(json.JsonObject obj, NumberValue value) {
        if (value.is(Float64)) {
            obj.put("asDouble", value.toFPLiteral());
        } else {
            obj.put("asInt", value.as(Int).toString());
        }
    }

    /**
     * Returns the OTLP integer code for an [AggregationTemporality] value:
     * `Delta = 1`, `Cumulative = 2`.
     */
    private static IntLiteral encodeTemporality(AggregationTemporality t) =
        t == AggregationTemporality.Delta ? 1 : 2;
}
