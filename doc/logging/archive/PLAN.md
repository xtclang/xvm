# lib_logging — Plan

> **Archived (2026-05).** This was the original master plan written when the
> branch was still a stub. Steps 1–8 have all landed; step 9 (compiler default
> name) and step 10 (`lib_logging_logback`) are tracked as Tier 3 in
> `../OPEN_QUESTIONS.md`. The current state is best understood from
> `../README.md` (entry point) and `../OPEN_QUESTIONS.md` (live tracking).
> Kept for history; no live decisions hang on the contents below.

This is the master plan for the experimental `lib_logging` module on the
`lagergren/logging` branch. It captures scope, non-goals, ordering of work, and the
explicit decisions the design is built on.

## Goals (in priority order)

1. **Instant familiarity for SLF4J users.** Anyone who has used SLF4J 2.x in Java should
   be able to read Ecstasy logging code and immediately know what it does. Same level set,
   same `{}` parameter substitution semantics, same throwable promotion rule, same
   marker / MDC concepts, same fluent event builder shape.

2. **Simple to use.** `@Inject Logger logger;` should be all most users ever write to get
   a working logger. No boilerplate factory calls, no static initializers, no compile-time
   config files in the default path.

3. **Same API/impl boundary as SLF4J.** `slf4j-api` is one jar; `slf4j-simple`,
   `logback-classic`, `log4j2-slf4j` are the bindings. We want the same separability so the
   default ships in the box but a richer backend can be swapped in without changing caller
   code. The boundary is the `LogSink` interface.

4. **Compatible with Ecstasy's injection model.** Loggers are obtained via
   `@Inject Logger`, optionally with a name (`@Inject("foo") Logger`). The platform/runtime
   controls which sink is wired up — same posture as `Console`, `Clock`, `Random`.

5. **Minimum-viable surface includes the things people *expect* even if rarely used.**
   Specifically: markers, MDC, fluent event builder. Default implementations may be
   no-ops, but the types must exist so user code that uses them compiles and runs against
   the default sink without modification.

6. **Future-ready for a logback-style backend.** Configuration-driven appenders, layouts,
   filters, hierarchical per-logger thresholds. Not implemented now, but designed-for so
   it's a swap-in (`LogSink` impl) rather than an API rewrite.

## Non-goals (for the v0 stub)

- Distributed tracing context propagation. (MDC carries strings; that's it.)
- Async / batched / buffered sinks. The contract allows them; the default doesn't do them.
- Configuration file format. The default sink has one knob (`rootLevel`) and that's it.
  A real config story belongs to the future logback-style backend module.
- Compile-time logger-name defaulting from the enclosing module. See `OPEN_QUESTIONS.md`.

## Module layout

```
lib_logging/
  build.gradle.kts
  src/main/x/
    logging.x                         module declaration
    logging/
      Level.x
      Marker.x
      BasicMarker.x
      MarkerFactory.x
      MDC.x
      LogEvent.x
      LogSink.x
      Logger.x
      LoggingEventBuilder.x
      LoggerFactory.x
      MessageFormatter.x
      BasicLogger.x
      BasicEventBuilder.x
      ConsoleLogSink.x
      NoopLogSink.x
      MemoryLogSink.x
  docs/
    PLAN.md                           this file
    DESIGN.md                         architecture
    SLF4J_PARITY.md                   API mapping
    ECSTASY_VS_JAVA_EXAMPLES.md       side-by-side
    CUSTOM_SINKS.md
    LOGBACK_INTEGRATION.md
    NATIVE_BRIDGE.md
    OPEN_QUESTIONS.md
```

The boundary between API and implementation is `LogSink.x`. Everything above is the
public, stable API; everything below is replaceable.

## Ordering of work

This is the order I expect the work to land in. The repository currently sits at the end
of step 2.

1. **Build skeleton.** `build.gradle.kts`, empty `logging.x`, registration in
   `xdk/settings.gradle.kts`, `gradle/libs.versions.toml`, `xdk/build.gradle.kts`.
   ✅ Done.
2. **Stub the API.** `Level`, `Marker`, `MarkerFactory`, `MDC`, `LogEvent`, `LogSink`,
   `Logger`, `LoggingEventBuilder`, `LoggerFactory`, `MessageFormatter`. All compile;
   bodies have `TODO(impl)` markers where appropriate. ✅ Done (modulo Ecstasy syntax
   review — see `OPEN_QUESTIONS.md`).
3. **Stub the default impls.** `BasicMarker`, `BasicLogger`, `BasicEventBuilder`,
   `ConsoleLogSink`, `NoopLogSink`, `MemoryLogSink`. ✅ Done.
4. **Make it build.** Run `./gradlew :lib_logging:build`, fix any Ecstasy syntax issues,
   confirm distribution build still works. **In progress.**
5. **Real `MessageFormatter`.** Port the SLF4J state machine for `{}` substitution
   including escape rules and throwable promotion. Test against SLF4J's published cases.
6. **Runtime injection plumbing.** Java side. Add a `RTLogger` native service in
   `javatools_jitbridge/src/main/java/org/xtclang/_native/logging/` and register it in
   `nMainInjector.addNativeResources()`. See `DESIGN.md` and `OPEN_QUESTIONS.md` — there
   is a wildcard-name lookup change required.
7. **Sample under `manualTests/`.** A throwaway program that does
   `@Inject Logger logger; logger.info(...)` end-to-end through the default
   `ConsoleLogSink`.
8. **MDC story.** Decide whether MDC entries are fiber-local or service-instance-local
   and document the decision; update the `MDC` service to match.
9. **Compiler-side default-name** (optional). When a user writes `@Inject Logger logger;`
   with no resourceName, have the XTC compiler substitute the enclosing module's
   qualified name. Strict ergonomics improvement; not blocking.
10. **Future module: `lib_logging_logback`.** Separate module shipping a
    configuration-driven `LogSink` with appenders, layouts, filters, per-logger
    thresholds. See `LOGBACK_INTEGRATION.md`.

## Validation checklist (done = "v0 ready to merge")

- [ ] `./gradlew clean` then `./gradlew :lib_logging:build` succeeds.
- [ ] `./gradlew xdk:installDist` still succeeds and bundles `lib_logging.xtc`.
- [ ] A `manualTests/` sample emits an `info` and `error` event to the console.
- [ ] `MemoryLogSink` test confirms the events captured contain the right
      `(loggerName, level, message, exception, marker)`.
- [ ] All existing XDK tests still pass.
