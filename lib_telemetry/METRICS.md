# Ecstasy Telemetry — Metrics

The metrics API follows the [OpenTelemetry Metrics specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md). It is organised into three layers:

| Layer | Description |
|-------|-------------|
| **API** | Interfaces your application code calls (`MeterProvider`, `Meter`, instruments) |
| **SDK** | Implementations that accumulate and export data (`InMemoryMeterProvider`, `OtlpJsonExporter`) |
| **Data model** | Immutable `const` records passed to exporters (`ResourceMetrics`, `Metric`, data point types) |

---

## Quick start

```ecstasy
// 1. Obtain a MeterProvider (swap for InMemoryMeterProvider in tests)
MeterProvider provider = NoOpMeterProvider.Global;

// 2. Get a Meter for your library/component
var meter = provider.getMeter("my-app", version = "1.0.0");

// 3. Create instruments
var requests = meter.createCounter(new InstrumentDescriptor("http.server.requests",
                                                             unit        = "{request}",
                                                             description = "Total HTTP requests served"));

var latency = meter.createHistogram(new InstrumentDescriptor("http.server.duration",
                                                              unit        = "ms",
                                                              description = "HTTP request latency"),
                                    bucketBoundaries = [5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0]);

// 4. Record measurements
requests.add(1, [new Attribute("http.method", "GET"), new Attribute("http.status_code", 200)]);
latency.record(42.5, [new Attribute("http.route", "/api/users")]);
```

---

## MeterProvider

`MeterProvider` is the entry point. Obtain one at application startup and share it globally. In production you will configure an SDK implementation; during testing use `InMemoryMeterProvider`.

```ecstasy
// Production: configure once
MeterProvider provider = ...; // injected or constructed

// Testing
var provider = new InMemoryMeterProvider(resource = new Resource([
    new Attribute(Resource.ServiceNameKey,    "my-service"),
    new Attribute(Resource.ServiceVersionKey, "2.0.0"),
]));
```

---

## Meter

A `Meter` is scoped to an **instrumentation scope** — typically the name and version of the library doing the instrumentation. All instruments created from the same `Meter` share that scope identity in exported data.

```ecstasy
var meter = provider.getMeter(
    "com.example.mylib",  // scope name — use a reverse-DNS style identifier
    version   = "1.2.3",
    schemaUrl = "https://opentelemetry.io/schemas/1.23.0");
```

---

## Synchronous instruments

Synchronous instruments are called inline where the event occurs.

### Counter

Counts things that only go up (requests, bytes sent, errors). Backed by `SumData` with `isMonotonic = True`.

```ecstasy
var bytesOut = meter.createCounter(new InstrumentDescriptor("http.response.body.size",
                                                             unit = "By"));

// In request handler:
bytesOut.add(response.body.size, [new Attribute("http.method", "POST")]);
```

> **Rule:** `value` must be non-negative. Use `UpDownCounter` if you need to subtract.

---

### UpDownCounter

Tracks a value that can rise and fall (active connections, queue depth). Backed by `SumData` with `isMonotonic = False`.

```ecstasy
var activeConns = meter.createUpDownCounter(
    new InstrumentDescriptor("http.server.active_requests", unit = "{request}"));

// On request start:
activeConns.add(1);

// On request end:
activeConns.add(-1);
```

---

### Histogram

Records the statistical distribution of a value (latency, payload size). Backed by `HistogramData` with explicit bucket boundaries.

```ecstasy
var dbLatency = meter.createHistogram(
    new InstrumentDescriptor("db.client.operation.duration", unit = "ms"),
    bucketBoundaries = [1.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0]);

Time start = clock.now;
db.query(sql);
dbLatency.record((clock.now - start).milliseconds.toFloat64(),
                 [new Attribute("db.system", "postgresql"),
                  new Attribute("db.operation", "SELECT")]);
```

The `bucketBoundaries` are advisory — the SDK may use or override them. If not provided, the SDK chooses defaults.

---

### Gauge (synchronous)

Records the current absolute value of a non-additive measurement. Backed by `GaugeData`.

```ecstasy
var cpuUsage = meter.createGauge(new InstrumentDescriptor("process.cpu.utilization",
                                                           unit = "1"));

// Called periodically or on-demand:
cpuUsage.record(getCpuUtilisation(), [new Attribute("cpu.mode", "user")]);
```

> Use `Gauge` when you have a synchronous opportunity to read the value. Use `ObservableGauge` if it is cheaper to read the value only when the SDK asks for it.

---

## Asynchronous (observable) instruments

Asynchronous instruments register a **callback** that the SDK invokes during each collection cycle. The callback calls its `observe` argument once per distinct attribute set.

```ecstasy
typedef MetricCallback as function void (function void (Measurement) observe);
```

### ObservableCounter

For monotonically increasing cumulative totals that are cheapest to read as a running total (e.g. CPU time, page faults). The callback reports the **cumulative total**, not a delta.

```ecstasy
var pageFaults = meter.createObservableCounter(
    new InstrumentDescriptor("process.paging.faults", unit = "{fault}"));

Closeable reg = pageFaults.observe(observe -> {
    observe(new Measurement(getPageFaultCount()));
});

// Later, to stop reporting:
reg.close();
```

---

### ObservableUpDownCounter

For additive values that can rise and fall and are cheapest to read as a snapshot total (e.g. heap size, active threads).

```ecstasy
var heapUsed = meter.createObservableUpDownCounter(
    new InstrumentDescriptor("process.runtime.jvm.memory.usage", unit = "By"));

heapUsed.observe(observe -> {
    observe(new Measurement(getHeapUsed(), [new Attribute("area", "heap")]));
    observe(new Measurement(getNonHeapUsed(), [new Attribute("area", "non_heap")]));
});
```

---

### ObservableGauge

For non-additive snapshot values (temperature, fan speed, utilisation percentages).

```ecstasy
var roomTemp = meter.createObservableGauge(
    new InstrumentDescriptor("environment.temperature", unit = "Cel"));

roomTemp.observe(observe -> {
    observe(new Measurement(thermometer.read(),
                            [new Attribute("location", "server-room")]));
});
```

---

## Attributes

Attributes are key-value pairs attached to a measurement to add dimensions (e.g. HTTP method, status code, service name). Use consistent attribute names across instruments for accurate aggregation.

```ecstasy
Attribute[] attrs = [
    new Attribute("http.request.method", "GET"),
    new Attribute("http.response.status_code", 200),
    new Attribute("server.address", "api.example.com"),
];
counter.add(1, attrs);
```

Attribute values are `AnyValue` — `String`, `Int`, `Float`, `Boolean`, `Byte[]`, or arrays/maps of the above.

---

## The `enabled` property

Before performing expensive work to compute a measurement, check `enabled`:

```ecstasy
if (latency.enabled) {
    var start = clock.now;
    doWork();
    latency.record((clock.now - start).milliseconds.toFloat64());
}
```

When no SDK is configured (e.g. using `NoOpMeterProvider`), `enabled` is always `False`, so measurement work is skipped entirely.

---

## Testing with InMemoryMeterProvider

Use `InMemoryMeterProvider` in unit tests. Call `collect()` after exercising the code under test to assert on the recorded measurements.

```ecstasy
@Test
void requestCounterIncrementsOnEachRequest() {
    var provider = new InMemoryMeterProvider();
    var meter    = provider.getMeter("my-app");
    var requests = meter.createCounter(new InstrumentDescriptor("http.requests"));

    handleRequest(meter);   // calls requests.add(1, [...])
    handleRequest(meter);

    ResourceMetrics[] snapshot = provider.collect();

    assert snapshot.size    == 1;
    assert snapshot[0].scopeMetrics.size == 1;

    Metric metric = snapshot[0].scopeMetrics[0].metrics[0];
    assert metric.name == "http.requests";

    SumData data = metric.data.as(SumData);
    assert data.dataPoints.size == 2;
    assert data.isMonotonic;
}
```

`collect()` clears each instrument's accumulator, so the next call returns only measurements recorded after the previous `collect()`.

### Configuring temporality

`InMemoryMeterProvider` defaults to **Delta** temporality for synchronous instruments,
matching the OTel SDK default. Each `collect()` cycle returns only measurements recorded
since the previous call, and accumulators reset afterward.

Switch to **Cumulative** temporality when your backend requires it. For example, when targeting 
Prometheus, which expects counters and histograms to be monotonically increasing by default 
(although it can be configured with experimental delta support):

```ecstasy
import telemetry.metrics.AggregationTemporality;

var provider = new InMemoryMeterProvider(
    resource    = new Resource([new Attribute(Resource.ServiceNameKey, "my-service")]),
    temporality = AggregationTemporality.Cumulative);
```

In Cumulative mode:
- **Counter / UpDownCounter:** `add()` calls accumulate per distinct attribute set into a
  running total. Each `collect()` returns one data point per attribute set without resetting
  it. `startTimeUnixNano` is fixed at construction and never changes.
- **Histogram / ExponentialHistogram:** count, sum, min, max, and bucket state accumulate
  across all collection cycles. `startTimeUnixNano` is fixed at construction.
- **Gauge:** unaffected — Gauge has no temporality concept.
- **ObservableCounter / ObservableUpDownCounter:** always Cumulative per the OTel
  specification, regardless of this setting.

---

## Exporting with ConsoleExporter

During development, wire a `ConsoleExporter` to print each collection cycle to stdout:

```ecstasy
var provider = new InMemoryMeterProvider();
var exporter = new ConsoleExporter();

// ... record measurements ...

exporter.export(provider.collect());
```

Output:

```
--- ResourceMetrics ---
  resource.service.name = my-service
  scope: com.example.mylib
    metric: http.server.requests [{request}]
      sum  monotonic=True  temporality=Delta
        value=42  attrs=[method=GET, status=200]
        value=7   attrs=[method=POST, status=201]
    metric: http.server.duration [ms]
      histogram  count=49  sum=2103.5  min=1.2  max=312.0
```

---

## Exporting via OTLP to a remote backend

`OtlpJsonExporter` serialises metrics as [OTLP/HTTP JSON](https://opentelemetry.io/docs/specs/otlp/)
and POSTs them to any compatible backend — Prometheus (via its OTLP ingestion endpoint),
Grafana Cloud, Honeycomb, Datadog, or a local OpenTelemetry Collector.

### Basic setup

```ecstasy
var exporter = new OtlpJsonExporter("https://otlp.example.com");

var provider = new InMemoryMeterProvider(resource = new Resource([
    new Attribute(Resource.ServiceNameKey,    "my-service"),
    new Attribute(Resource.ServiceVersionKey, "1.0.0"),
]));

var meter    = provider.getMeter("com.example.mylib", version = "1.0.0");
var requests = meter.createCounter(new InstrumentDescriptor("http.server.requests",
                                                             unit = "{request}"));

// Record measurements in your request path...
requests.add(1, [new Attribute("http.method", "GET")]);

// Periodically flush to the backend (e.g. every 60 seconds):
exporter.export(provider.collect());
```

The exporter POSTs to `{endpoint}/v1/metrics` with `Content-Type: application/json`.

### Authenticating with HTTP Basic Auth

Most managed OTLP endpoints (Grafana Cloud, Coralogix, etc.) require credentials. HTTP
Basic Auth encodes a username and API key (or password) as a Base64 string and places it
in the `Authorization` header.

```ecstasy
import convert.codecs.Utf8Codec;
import convert.formats.Base64Format;

String username = "my-user";
String apiKey   = "glc_eyJ...";   // your API key or password

String credentials = Base64Format.Instance.encode(Utf8Codec.encode($"{username}:{apiKey}"));
String authHeader  = $"Basic {credentials}";

var exporter = new OtlpJsonExporter(
    "https://otlp-gateway-prod-eu-west-0.grafana.net/otlp",
    headers = ["Authorization" = authHeader]);
```

> **Tip:** Never hard-code credentials in source. Read them from an environment variable or
> injected configuration at startup.

### Authenticating with a Bearer token

Some backends (Honeycomb, cloud-native collectors) use a bearer token instead:

```ecstasy
String token = "hcaik_...";   // your API token

var exporter = new OtlpJsonExporter(
    "https://api.honeycomb.io",
    headers = [
        "Authorization"    = $"Bearer {token}",
        "x-honeycomb-team" = token,   // Honeycomb also accepts this header
    ]);
```

### Sending to a local OpenTelemetry Collector

A local Collector (listening on the default OTLP/HTTP port) needs no authentication:

```ecstasy
var exporter = new OtlpJsonExporter("http://localhost:4318");
```

The Collector can then forward to any backend, add resource attributes, and batch
requests — useful when the backend changes independently of the application.

### Connecting to Prometheus

Prometheus v2.47+ can scrape OTLP metrics directly via its remote-write OTLP ingestion
endpoint. Prometheus expects **Cumulative** counters and histograms — configure your
`InMemoryMeterProvider` accordingly before exporting:

```ecstasy
var provider = new InMemoryMeterProvider(
    resource    = new Resource([new Attribute(Resource.ServiceNameKey, "my-service")]),
    temporality = AggregationTemporality.Cumulative);

var exporter = new OtlpJsonExporter(
    "http://prometheus:9090/api/v1/otlp",
    headers = ["Authorization" = $"Basic {credentials}"]);   // if auth is enabled

// Flush loop — cumulative state accumulates; Prometheus sees monotonically increasing values
exporter.export(provider.collect());
```

### Full wiring example

```ecstasy
service MetricsService {

    private InMemoryMeterProvider provider;
    private OtlpJsonExporter      exporter;
    public/private Meter          meter;

    construct(String otlpEndpoint, String authHeader) {
        this.provider = new InMemoryMeterProvider(resource = new Resource([
            new Attribute(Resource.ServiceNameKey, "order-service"),
        ]));
        this.exporter = new OtlpJsonExporter(otlpEndpoint,
                                             headers = ["Authorization" = authHeader]);
        this.meter    = provider.getMeter("com.example.orders", version = "2.0.0");
    }

    /**
     * Flush all accumulated measurements to the OTLP backend.
     * Call this on a timer (e.g. every 60 seconds) or at shutdown.
     */
    Result flush() {
        return exporter.export(provider.collect());
    }
}
```

### Constructor reference

```
OtlpJsonExporter(
    String              endpoint,            // base URL, e.g. "https://otlp.example.com"
    Map<String, String> headers    = [],     // extra request headers
    Int                 maxRetries = 3)      // retry attempts on transient errors
```

Transient errors (`429 Too Many Requests`, `502`, `503`, `504`) are retried up to
`maxRetries` times. `400 Bad Request` (malformed payload) is not retried. After
`shutdown()` is called, `export()` returns `DropData`.

---

## Implementing a custom MetricExporter

Implement `MetricExporter` to send metrics to any backend (OTLP, Prometheus, StatsD, etc.).

```ecstasy
service MyOtlpExporter
        implements MetricExporter {

    private Boolean active = True;

    @Override
    Result export(ResourceMetrics[] metrics) {
        if (!active) {
            return DropData;
        }
        // Serialise and send metrics to the backend...
        return Success;
    }

    @Override
    void forceFlush() {
        // Wait for in-flight sends to complete...
    }

    @Override
    void shutdown() {
        active = False;
    }
}
```

---

## Package layout

```
telemetry.xtclang.org                      Module root (telemetry.x)
└── metrics                                Metrics sub-package
    ├── MeterProvider                      Entry point interface
    ├── Meter                              Instrument factory
    ├── Counter / UpDownCounter            Synchronous additive instruments
    ├── Histogram                          Synchronous distribution instrument
    ├── Gauge                              Synchronous snapshot instrument
    ├── ObservableCounter / ...Gauge / ... Asynchronous instruments
    ├── InstrumentDescriptor               Name / unit / description
    ├── Measurement                        Value + attributes (async callbacks)
    ├── MetricCallback                     Typedef for async callback functions
    ├── [Data model]                       AggregationTemporality, NumberDataPoint,
    │                                      GaugeData, SumData, HistogramData,
    │                                      ExponentialHistogramData, SummaryData,
    │                                      Exemplar, Metric, ScopeMetrics,
    │                                      ResourceMetrics, DataPointFlags
    ├── noop/                              No-op SDK (default, zero overhead)
    │   └── NoOpMeterProvider.Global       Use when no backend is configured
    ├── memory/                            In-memory SDK (for tests)
    │   └── InMemoryMeterProvider          Create, record, collect(), assert
    └── export/                            Exporter contracts
        ├── MetricExporter                 Interface (export / forceFlush / shutdown)
        ├── ConsoleExporter                Human-readable stdout exporter
        └── otlp/                          OTLP/HTTP JSON exporter
            ├── OtlpJsonExporter           POST to any OTLP endpoint (with auth, retries)
            └── OtlpEncoding               Pure data-model → json.Doc conversion helpers
```

---

## Choosing the right instrument

| What you are measuring | Instrument |
|------------------------|------------|
| Events that only accumulate (requests, errors, bytes) | `Counter` |
| Values that rise and fall (active requests, queue depth) | `UpDownCounter` |
| Statistical distribution (latency, size) | `Histogram` |
| Non-additive current value, recorded inline | `Gauge` |
| Monotonic total, read on demand from the system | `ObservableCounter` |
| Additive total, read on demand from the system | `ObservableUpDownCounter` |
| Non-additive current value, read on demand | `ObservableGauge` |
