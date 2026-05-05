# Cloud-native logging — why API familiarity matters

This doc explains *why* the choice between SLF4J-shaped and slog-shaped logging
APIs (`lib_logging` vs `lib_slogging`, see `lib-logging-vs-lib-slogging.md`) is
not just an aesthetic call. It is the entry point to the major cloud
observability ecosystems — and the wrong API choice silently rules Ecstasy out
of common deployment patterns even when the code itself is fine.

The doc is written assuming the reader is an expert systems engineer — runtimes,
compilers, JIT — but has not personally deployed a service to GCP / AWS / Azure
in production. That is a common, perfectly reasonable shape; the cloud
ecosystems are sprawling and most of the day-to-day knowledge lives in
operations teams, not language teams. So we start by laying out what
"deploying an application" looks like in 2026, then explain how logging fits
into that, then come back to "and that is why this API choice matters."

## A brief geography of how applications are deployed today

For a long time — through about 2010 in mainstream practice — the unit of
software delivery was an **installer that produced a process on a machine**: an
MSI on Windows, a `.deb` on Linux, an installed shell command. Users started
the process, the process wrote log lines to a file under `/var/log/` or
`%PROGRAMDATA%\<app>\logs`, and ops people logged into the machine and tailed
the file when something went wrong. The logging API's job was to format text
into that file.

That world still exists for desktop applications and CLI tools (the XDK itself
is delivered this way, and rightly so). But for **server-side applications and
services** — anything that handles requests, reads from a database, calls a
peer service — the deployment model has been replaced by a fundamentally
different shape:

1. The application is built into a **container image** (most often via Docker /
   OCI). The image is a self-contained filesystem with the runtime, the app,
   and its dependencies — conceptually "an executable, but for a Linux
   sandbox."
2. The image is run not on one machine but on a **cluster orchestrator**: GKE
   (GCP), EKS (AWS), AKS (Azure), or a managed equivalent (Cloud Run, App
   Runner, Container Apps). The orchestrator decides which physical node runs
   the container, can run dozens or thousands of replicas, and replaces them
   automatically when nodes fail.
3. There is **no persistent local filesystem** the application can rely on for
   logs. Containers come and go; their disks are wiped between restarts. A
   log file inside a container is gone the moment that container dies — which
   for a healthy fleet might be every 12 hours.
4. So, by convention, **the application writes structured log records to its
   own stdout/stderr** as one record per line. The orchestrator captures
   those streams and forwards them, fully automatically, to a managed
   logging product (Cloud Logging, CloudWatch Logs, Azure Monitor) — which
   indexes them, makes them queryable through a web UI, sends alerts when
   patterns match, and retains them per a configured policy.

Two consequences flow from (4) and shape every modern logging API:

- **Logs are queried, not tailed.** Operations engineers don't `ssh` into the
  machine and `tail -f`; they open a web console, type a structured query
  ("show me all `ERROR`-level events in the last hour with `requestId=r_42`"),
  and the cloud product returns results across thousands of containers as one
  search.
- **Each log line is consumed as a record, not as text.** The cloud product
  parses the line as a JSON object (or an equivalent structured format) and
  indexes every field. A string `"INFO  user u_3 logged in"` is a single
  searchable string. A JSON object `{"level":"INFO","user":"u_3","action":"login"}`
  has every field independently queryable, filterable, groupable, and
  alert-able.

The shift from "logs are text in a file" to "logs are queryable JSON streams"
is the single largest operational change in server software since the year
2000. Every popular logging API since 2014 (slf4j-2.x, log4j2, slog,
zap, tracing, Serilog) was retrofitted or freshly designed around it.

## What "the cloud product on the other side" actually does

Pick GCP Cloud Logging as the canonical example. When a container's stdout
emits a JSON line, the GKE node's logging agent (`fluent-bit` by default)
ships it to the Cloud Logging API. The product then:

1. **Parses** the JSON. Each top-level key becomes an indexed field.
2. **Maps** specific keys to its own first-class concepts:
   - `severity` (or sometimes `level`) — must be one of
     `DEBUG / INFO / WARNING / ERROR / CRITICAL`. Drives the colour of the row
     and feeds alert policies.
   - `message` (or `msg`) — the human-readable text shown in the UI's main
     column.
   - `trace` and `spanId` — if present, link the log entry to a distributed
     trace (Cloud Trace, Jaeger). Click the row, see the entire request flow.
   - `labels.*` — a string-only key/value map exposed as filterable tags in
     the UI (the same role as Logback's `Marker` plays in older configs).
   - `jsonPayload.*` — anything else, fully indexed and queryable.
3. **Persists** the record according to retention policy (30 days default
   for the basic tier, up to 10 years on the audit tier).
4. **Indexes** the fields for search, alerting, and metric extraction.

CloudWatch Logs (AWS) and Azure Monitor (Application Insights) work the same
way — different product names, near-identical conventions. JSON in, structured
search out.

The key takeaway: the cloud-side product **reads the structured fields the
logging API put on the wire**. If the API never emits a `requestId` field,
you can't filter on it. If the API emits everything as one big string, all
querying degrades to substring search. The expressiveness of the logging API
sets a hard ceiling on the operability of the deployed system.

## The standard adapters — where API familiarity lives

The logging APIs we are comparing exist not in isolation but in a
*pre-existing graph of adapters* that translate them into the schemas the
cloud products expect.

### Google Cloud Logging

- **SLF4J / Logback**: Google ships [`com.google.cloud.logging.logback.LoggingAppender`](https://cloud.google.com/logging/docs/setup/java).
  Drop it into your `logback.xml`, and every Logback `LoggingEvent` becomes a
  Cloud Logging entry with `severity` mapped from level, `message` mapped from
  the formatted message, MDC keys mapped to `jsonPayload`, **markers mapped to
  `labels`**, and trace IDs mapped from the SLF4J `MDC` keys
  `traceId`/`spanId`/`traceSampled`. Existing Java code that already used SLF4J
  needs *no source changes* to send logs to Cloud Logging — it is a config
  file change.
- **Go `log/slog`**: [`cloud.google.com/go/logging.HandlerOptions`](https://pkg.go.dev/cloud.google.com/go/logging#HandlerOptions)
  produces a `slog.Handler` that writes records directly to the Cloud Logging
  API. slog `Attr`s become `jsonPayload` fields; the slog level numbers map
  cleanly to GCP's severity ladder.

### AWS CloudWatch Logs

- **SLF4J / Logback**: the community-maintained [`cloudwatch-logback-appender`](https://github.com/j256/cloudwatchlogbackappender)
  or the more common pattern of `logstash-logback-encoder` writing JSON to
  stdout, which the Lambda runtime / ECS Firelens picks up automatically. MDC
  keys become top-level JSON fields; markers go into a `markers` array. AWS
  X-Ray trace IDs propagate through the same MDC channel.
- **Go `log/slog`**: stdout JSON is the default; AWS Lambda's runtime captures
  it directly. Alternatively, the [`slog-cloudwatch`](https://pkg.go.dev/github.com/jutkko/slog-cloudwatch)
  community handler writes to CloudWatch Logs directly.

### Azure Monitor / Application Insights

- **SLF4J / Logback**: [`applicationinsights-logging-logback`](https://learn.microsoft.com/en-us/azure/azure-monitor/app/java-standalone-arguments)
  ships every `LoggingEvent` as a `TraceTelemetry`. MDC becomes "custom
  properties"; **Logback markers become custom properties** keyed
  `markerName -> "true"`. App Insights' Kusto query language indexes them all.
- **Go `log/slog`**: any slog handler that writes JSON to stdout works
  through Azure Container Apps' built-in log capture; community packages also
  exist.

The pattern is identical across the three providers: the API's concepts
(level, MDC keys, markers, structured fields) are pre-mapped to the cloud
product's concepts (severity, custom properties, labels, indexed JSON
payload) **by adapters that already exist and are battle-tested**.

## Why this matters for the `lib_logging` vs `lib_slogging` decision

If Ecstasy chooses an API shape that is *isomorphic* to one of these
two ecosystems, the path to "drop in an Ecstasy server, view its logs in
Cloud Logging / CloudWatch / App Insights" is "write a thin adapter that
translates `LogEvent` / `Record` to the provider's wire format" — typically a
weekend project, often community-maintained.

If Ecstasy chooses a *novel* shape — well-designed, perhaps cleaner than
either — every prospective adopter has to write a custom adapter from scratch
for every provider they care about, no community will share that work, and
every team's adapter is a private liability they have to maintain. The
"cleaner" API ends up gated on the willingness to maintain a parallel
ecosystem of adapters.

Concrete read on the two libraries' positions:

- **`lib_logging`** (SLF4J shape) gets the largest ready-made adapter graph.
  Every JVM team's mental model maps onto it: `Logger`, `MDC`, `Marker`,
  `Level`, fluent builder. The cloud-side schema mappings (level → severity,
  MDC → jsonPayload, marker → labels) carry over essentially untouched. A
  prospective Ecstasy user who has been writing SLF4J in Java for fifteen
  years can deploy an Ecstasy service to GCP and have its logs render
  correctly in the Cloud Logging UI with `requestId` filterable, without
  having to learn a new mental model.

- **`lib_slogging`** (Go slog shape) gets the second-largest adapter graph.
  Cloud Logging, CloudWatch, and App Insights all have native slog handlers.
  The audience is narrower — cloud-native Go engineers — but it is the
  fastest-growing audience in production observability, and the slog
  attribute model maps to JSON-payload-shaped logging *more* directly than
  SLF4J does (no markers / MDC reconciliation step; everything is already an
  attribute).

A library that picks neither shape — say, a clean-slate Ecstasy-native API
based on tagged unions and `SharedContext` — *can* be built, and may even be
simpler to use in pure Ecstasy, but it ships with no adapter graph at all.
The first user who tries to deploy to Cloud Logging finds out the hard way
that they're writing the integration themselves.

## What "do the right thing for cloud" means in API terms

Independent of the SLF4J-vs-slog decision, here are the API features that
every modern cloud product relies on, ordered by how badly things break if
the API doesn't expose them:

1. **Per-event structured fields.** Every emission must be able to carry
   key/value pairs that survive verbatim into the wire format. SLF4J 2.x's
   `KeyValuePair` list and slog's `Attr` both satisfy this. **Both
   `lib_logging` and `lib_slogging` already do.**
2. **A field for the trace/span ID.** Distributed tracing is now table
   stakes; the logging product correlates trace IDs to render the request
   flow. SLF4J does this through MDC keys (`traceId`, `spanId`) by
   convention; slog through dedicated attrs. **Both libraries can carry
   these; the integration code chooses which keys to use.**
3. **A canonical level ladder that maps to provider severity.** GCP wants
   `DEBUG / INFO / WARNING / ERROR / CRITICAL`. SLF4J's five levels map
   directly. slog's open `Int` levels map via comparison. **Both fine.**
4. **A way to attach context without threading parameters.** SLF4J's MDC,
   slog's `Logger.With(...)` chains. Without this, request-scoped fields
   (like `requestId`) need to be passed through every function, which is
   a non-starter for real applications. **Both libraries provide this in
   different ways — see the comparison doc § 3.2.**
5. **An async / batched sink.** When a service emits 10k log events per
   second, blocking the request thread on disk I/O is unacceptable. Both
   libraries' `AsyncLogSink` / `AsyncHandler` wrappers cover this.

Note that *all five* of these features exist for the cloud-deployment story,
not for the local-tail-the-log-file story. They are exactly the features that
were retrofitted onto SLF4J between 2018 and 2022, and the features that
slog was designed to have from day one.

## TL;DR

Choosing between `lib_logging` (SLF4J) and `lib_slogging` (slog) is **not** a
question about which API reads better in isolation. It is a question about
which existing cloud-side adapter ecosystem an Ecstasy deployment plugs into
on day one. SLF4J's adapter graph is larger and more mature; slog's is
younger, growing fastest, and has a slightly cleaner mapping into structured
JSON output. Either choice is defensible; a third "cleaner" choice means
shipping into an empty ecosystem and writing the integrations yourself.

This document exists so that decision is made with that context, not without
it.


---

_See also [README.md](README.md) for the full doc index and reading paths._
