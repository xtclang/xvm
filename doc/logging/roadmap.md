# Logging roadmap

> **Audience:** reviewers deciding whether to merge this branch and what should
> follow. Read this once before [`open-questions.md`](open-questions.md).

This page is the single answer to *"what blocks merge, what lands in v1, what
is explicit future?"*. The detailed history of each item — alternatives
considered, rejected variants, who pushed back — lives in
[`open-questions.md`](open-questions.md). Treat that as the supporting evidence,
this as the index.

## Status legend

| Mark | Meaning |
|---|---|
| ✓ | Done in this branch — code, tests, docs all present |
| ~ | Partial — runtime works, compiler/tooling polish missing |
| → | Planned for v1 (post-merge) |
| + | Future, no commitment |
| ? | Open — needs a decision before merge |

## Merge-blocking

Items the reviewers must resolve before this branch can graduate from POC.

| Item | State | Where |
|---|---|---|
| Pick `lib_logging` (SLF4J shape) vs `lib_slogging` (slog shape) as the canonical XDK API. | ? | [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md), open question Q-D6 |
| Confirm the `const`/`service` rule for SPI implementations is idiomatic XDK. | ? | open question Q-D1 |
| Confirm `@Inject` inside a `const` is fully supported (semantics, fiber affinity, freeze). | ? | open question Q-D2 |
| Confirm per-fiber `MDC` via `SharedContext<immutable Map>` matches intended use. | ? | open question Q-D4 |
| Decide whether cross-module default-arg synthesis on `const` constructors is a bug or expected. | ? | open question Q-D7 |

## v1 (post-merge, before users depend on it)

Items the chosen library must land before downstream code starts to migrate.

| Item | State | Notes |
|---|---|---|
| Compiler-synthesized default logger names. | → | Runtime fallback derives from caller namespace today; the compiler should emit the exact module/class name as the resource name. R10 in the README. |
| Compiler-synthesized source location. | → | `Logger.logAt(...)` is the lowering target. `logger.info(...)` calls do not yet get sourceFile/sourceLine populated automatically. R10 in the README. |
| `AsyncLogSink.drain` — `try/finally` to release `draining` on delegate exception. | → | Defensive: `LogSink.log` is contractually non-throwing, but the async wrapper should recover from a misbehaving delegate. Code review item. |
| `AsyncLogSink` queue dequeue performance — replace `Array.delete(0)` (O(n)) with a circular buffer. | → | Currently O(n²) per drain; only matters at scale. |
| Strip the asymmetric `xdk-logging` dep from `javatools_bridge/build.gradle.kts` (or add `xdk-slogging` for parity). | → | Bridge `_native.x` imports neither. Code review item. |
| Migrate `~/src/platform` ad-hoc `console.print($"… Info :")` lines to the chosen `Logger`. | → | Survey done in [`usage/platform-and-examples-adaptation.md`](usage/platform-and-examples-adaptation.md). |
| Confirm `addResourceSupplier` lookup performance is fine (O(n) loop over ~20 entries vs the previous HashMap by name). | → | Likely a non-issue, but worth checking once the resource table grows. |

## Future, no commitment

Items deliberately out of scope for the base library. They are pure-XTC modules
built on top of the SPI; nothing about them belongs in `lib_logging` itself.

| Item | State | Where |
|---|---|---|
| Configuration-file driven backend (`lib_logging_logback`). | + | Sketched in [`future/logback-integration.md`](future/logback-integration.md) |
| Rolling/network/file destinations. | + | Live behind `LogSink`; out of scope for the base lib. |
| Provider-specific cloud sinks (GCP Cloud Logging, CloudWatch, App Insights). | + | Each is a thin adapter over `LogEvent`. See [`cloud-integration.md`](cloud-integration.md). |
| Distributed tracing context propagation (trace/span IDs as a first-class concern). | + | MDC carries strings; tracing is a separate library. |
| XTC linter for log-statement footguns (`info("count: " + n)`, missing exception arg). | + | Linter feature, not a library feature. Open question 12. |
| Adapter modules (`lib_logging_slf4j_adapter`, `lib_logging_slogging_adapter`) bridging the losing API onto the canonical one. | + | Only if the losing POC has users worth migrating. |
| `service MarkerRegistry` for fiber-safe global marker interning + DAG mutation. | + | The current `class MarkerFactory` covers the per-component pattern; a service-shaped registry would let multiple fibers share one interned marker space. See [`MarkerFactory.x`](../../lib_logging/src/main/x/logging/MarkerFactory.x) "Why a class, not a service" for the design constraint that keeps these as separate types. |

## Reading order

Once you have read this page:

- For the API-choice debate: [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md).
- For the open language/runtime questions: [`open-questions.md`](open-questions.md).
- For the cloud-side context behind the merge decision: [`cloud-integration.md`](cloud-integration.md).

---

Previous: [`README.md`](README.md) | Next: [`lib-logging-vs-lib-slogging.md`](lib-logging-vs-lib-slogging.md) →
