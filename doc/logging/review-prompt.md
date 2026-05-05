# AI review prompt for the logging POC branch

Use this prompt with any AI coding/review agent to review the entire
`lagergren/logging` branch, with special attention to whether the documentation clearly
teaches the proposed XDK logging design.

```text
You are reviewing the `lagergren/logging` branch of the XDK repository.

Your job is to perform a code-and-documentation review, not to rewrite the branch unless
explicitly asked. Treat this as a senior engineering review. Prioritize correctness,
clarity, missing rationale, misleading examples, broken links, implementation/documentation
drift, and places where a skeptical reviewer would get confused.

Branch intent
-------------
This branch contains two parallel proof-of-concept logging libraries:

- `lib_logging/`: SLF4J/Logback-shaped Ecstasy logging. This is currently recommended
  as the canonical XDK logging facade.
- `lib_slogging/`: Go `log/slog`-shaped Ecstasy logging. This exists as a comparison
  POC, not as a proposed permanent second default facade.

The branch's central design claim is:

1. XDK code should eventually have one injectable logging facade named `lib_logging`.
2. Application/library code should use `@Inject Logger logger;`.
3. The host/container should own backend policy by injecting or configuring a sink/handler.
4. Structured logging, dynamic backend configuration, JSON/cloud output, async fanout,
   redaction, and test capture are requirements for a modern logging design.
5. The branch currently recommends the SLF4J-shaped API because named loggers, `{}`
   formatting, markers, MDC, fluent builders, and Logback-style backend configuration are
   familiar to the broadest likely audience.

Review order
------------
Start from `doc/logging/README.md`. It should be possible to understand the design at a
first-pass level by reading that file alone. Then follow the links in this order:

1. `doc/logging/lib-logging-vs-lib-slogging.md`
2. `doc/logging/usage/structured-logging.md`
3. `doc/logging/usage/configuration.md`
4. `doc/logging/api-cross-reference.md`
5. `doc/logging/open-questions.md`
6. `lib_logging/README.md`
7. `lib_slogging/README.md`

After the first pass, read the deep-dive docs as needed:

- `doc/logging/design/design.md`
- `doc/logging/design/why-slf4j-and-injection.md`
- `doc/logging/design/xdk-alignment.md`
- `doc/logging/cloud-integration.md`
- `doc/logging/usage/injected-logger-example.md`
- `doc/logging/usage/ecstasy-vs-java-examples.md`
- `doc/logging/usage/slf4j-parity.md`
- `doc/logging/usage/slog-parity.md`
- `doc/logging/usage/custom-sinks.md`
- `doc/logging/usage/custom-handlers.md`
- `doc/logging/usage/platform-and-examples-adaptation.md`
- `doc/logging/future/logback-integration.md`
- `doc/logging/future/native-bridge.md`
- `doc/logging/future/lazy-logging.md`
- `doc/logging/future/runtime-implementation-plan.md`

Also inspect the implementation enough to verify the docs:

- `lib_logging/src/main/x/logging/`
- `lib_logging/src/test/x/LoggingTest/`
- `lib_slogging/src/main/x/slogging/`
- `lib_slogging/src/test/x/SLoggingTest/`
- `manualTests/src/main/x/TestLogger.x`
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java`

Specific questions to answer
----------------------------
1. Can a first-time reviewer understand from `doc/logging/README.md`:
   - why two libraries exist in this branch,
   - why only one should survive as the canonical `lib_logging`,
   - what is already implemented,
   - what is only sketched or future work,
   - and why the branch currently recommends the SLF4J-shaped API?

2. Does the documentation flow logically from overview to API comparison to structured
   logging to configuration/backend details? Identify any document that forces a deep
   dive before the reader has the basic model.

3. Are verbose sections placed in the right deep-dive documents, with concise summaries
   near the top? Flag any "wall of text" that should be summarized, moved, or split.

4. Are all public claims about implemented functionality true in the code?
   Check especially:
   - `MessageFormatter`
   - `Logger.logAt(...)` and source metadata
   - `JsonLogSink` / `JsonLogSinkOptions`
   - `CompositeLogSink`, `HierarchicalLogSink`, `AsyncLogSink`
   - slog `JSONHandler`, `HandlerOptions`, `AsyncHandler`, `LoggerContext`
   - runtime injection in `NativeContainer.java`

5. Are proposed future pieces clearly labeled as future work?
   Examples: config-file parser, property/env/CLI override merge, file and rolling-file
   sinks, provider-specific cloud clients, automatic source capture, compiler-generated
   logger names, and a Java Logback native bridge.

6. Does the comparison between SLF4J/Logback and Go `slog` stay technically fair?
   Verify especially:
   - slog has attrs/groups/handlers/open levels but no first-class markers,
     hierarchical logger-name tree, or message formatter.
   - SLF4J/Logback has named loggers, markers, MDC, message templates, and mature
     configuration, but a less uniform structured-data model.
   - Java ecosystems such as Log4j 2, Flogger, Spring Boot logging, JUL, Commons
     Logging, and logstash-logback-encoder are described accurately.

7. Are Java/Go/Ecstasy examples readable, realistic, and clearly marked as either
   implemented POC code or proposed API sketches?

8. Is the XDK module story clear?
   The expected direction is:
   - core facade/SPI in the eventual `lib_logging`,
   - config loading in a first-class follow-up module such as `lib_logging_config`,
   - file destinations in a module such as `lib_logging_file`,
   - cloud destinations either in `lib_logging_cloud` or provider-specific modules,
   - runtime/compiler polish outside the logging library itself.

9. Are there broken or stale local links, stale test counts, stale branch-status claims,
   or references to old implementation plans that now conflict with the branch?

10. Do the docs use requirement language consistently? Early requirements should use
    MUST/MUST NOT where they are normative.

Useful local checks
-------------------
Run these if the environment supports them:

- `git status --short`
- `git log --oneline --decorate -20`
- `git diff --check`
- `./gradlew :xdk:lib-logging:compileXtc :xdk:lib-slogging:compileXtc --console=plain`
- `./gradlew :xdk:lib-logging:test :xdk:lib-slogging:test --console=plain`
- `./gradlew :javatools:compileJava :manualTests:runOne -PtestName=TestLogger --console=plain`

For local Markdown links, use any equivalent link checker or script. A simple review is
enough if tooling is unavailable.

Output format
-------------
Return findings first, ordered by severity:

- `High`: incorrect implementation claim, misleading recommendation, broken core example,
  or documentation flow that prevents understanding the proposal.
- `Medium`: important missing explanation, stale status, unclear implemented-vs-future
  boundary, or incomplete comparison.
- `Low`: wording, formatting, small link/index issue, minor redundancy.

For each finding, include:

- file path and line number,
- the problem,
- why it matters,
- a concrete suggested fix.

After findings, include:

- "Reader-flow summary": whether the README -> comparison -> structured logging ->
  configuration path works.
- "Implementation/doc drift": any mismatch between docs and code.
- "Suggested next edits": a short prioritized list.
- "Tests/checks run": commands run and results, or explain why they were not run.

Do not produce a long rewrite unless asked. Focus on review signal.
```
