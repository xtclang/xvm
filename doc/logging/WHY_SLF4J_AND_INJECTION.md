# Why model `lib_logging` on SLF4J and on Ecstasy injection

This is the rationale doc. It exists to make the case that the two big shape-defining
choices in `lib_logging` — _model the API on SLF4J_, _acquire loggers via `@Inject`_ —
are not arbitrary preferences but the only choices that combine into a logging library
worth shipping. Everything else has been tried, in many languages, and falls short in
specific, predictable ways.

## TL;DR

- **Modeling on SLF4J means we inherit thirty years of hard-won design.** SLF4J is the
  one logging API in the Java ecosystem that survived the wars (log4j 1.x → log4j 2 →
  Logback → JUL → JBoss Logging → ...). It survived because it got the API↔impl boundary
  right. Copying that boundary copies the wins.
- **Modeling injection on `@Inject` means user code never depends on a backend.** This is
  the thing static `LoggerFactory.getLogger` *almost* gives you in Java but never quite
  does — there is always a static binding choice somewhere on the classpath. In Ecstasy
  the runtime container provides the binding. There is no classpath race.
- **The combination is greater than the sum of the parts.** Ecstasy gives us
  per-container injection; SLF4J gives us a stable user-facing API. Together: a logging
  library where the host application controls every embedded module's logging without
  any embedded module having to know.

The rest of this document expands each of those.

---

## Part 1 — Why SLF4J is the right model

### 1.1 The API/impl boundary is the only thing that has ever held up

Logging in Java has churned through at least seven serious facades and frameworks:
`System.err.println`, log4j 1.x, JUL (`java.util.logging`), Apache Commons Logging,
JBoss Logging, log4j 2, SLF4J + Logback. Each replacement had a moment of "this is the
new way". Most of them are now legacy.

The thing that has stuck is the **SLF4J API**. Logback replaced log4j as the canonical
backend. log4j 2 came along. Then `slf4j-jul-impl`, `log4j-slf4j-impl`, etc. — bridges
in every direction. None of this churn touched user code. The reason is that SLF4J got
exactly one thing right that Commons Logging got wrong and JUL got wrong: **the
caller-facing API is one stable shape, and the binding is a separate jar selected at
deploy time.** Caller code does not import the binding; it cannot, by construction.

This is the decision we must inherit, because the decision pays for itself for decades.
A new language that ships a logging library that mixes API and binding (the way JUL
does, the way Python's stdlib `logging` does) has just signed up for the same churn.

### 1.2 SLF4J's specific design choices, why they're each load-bearing

The signature shapes look small but they encode hard-won lessons. Walking through them:

- **`{}` placeholder formatting**, *not* `String.format`. `String.format` is expensive
  even when the level is disabled. The whole reason SLF4J exists is that log4j 1.x users
  paid the cost of `"some msg " + obj.toString()` even when DEBUG was off. Deferred
  formatting via `{}` placeholders + arrays of arguments lets the implementation skip
  the work entirely when `isDebugEnabled()` is false. This is the original SLF4J
  motivation; it is still load-bearing.

- **Throwable promotion**. The trailing `Throwable` is *not* a substitution argument; it
  is the cause. This rule looks small but it eliminates an entire class of footgun
  ("why is my stack trace just `java.io.IOException@1c2d4f` in the message?"). SLF4J
  users learn this rule once.

- **Level checks as first-class methods.** `if (log.isInfoEnabled())` is the standard
  Java idiom for "this log call has an expensive argument I want to elide". Frameworks
  that lack this method force callers to either pay the formatting cost or write
  unreadable wrapper code.

- **Markers as a structured tag.** Markers are how you say "this event belongs to the
  AUDIT category" without resorting to either a separate logger name or substring
  matching on messages. Backend filters can route on markers without grep.

- **MDC for context propagation.** MDC is _the_ idiom for request-scoped context.
  Every observable Java service stack uses it. If you want a log line tagged with
  `requestId` without your business code having to thread it through, you set it once
  in the request handler and the encoder picks it up.

- **The fluent event builder (SLF4J 2.x).** Eight years after SLF4J 1.x shipped, the
  community decided the explosion of overload signatures (`info(String)`, `info(String,
  Object)`, `info(String, Object, Object)`, `info(Marker, ...)`, ...) was a mistake. The
  fluent builder is the answer: one builder type with `addArgument`, `addMarker`,
  `setCause`, `addKeyValue`, then `log()`. SLF4J kept the legacy overloads for
  backwards compatibility, but new code is moving to the builder. We get to skip the
  legacy and ship the builder as a first-class option from day one.

Every one of these is in `lib_logging`. None of them is novel; all of them are
load-bearing. Throw any one out and we have a worse logging library.

### 1.3 The alternatives, why none of them is good enough

| Model | Why we don't copy it |
|---|---|
| **`java.util.logging` (JUL).** The "ship something with the language" approach. | Levels are weird (`FINE`/`FINER`/`FINEST` instead of `DEBUG`/`TRACE`); MDC support was added late and is awkward; configuration is a flat properties file with no programmatic equivalent; performance is poor on hot paths. JUL is the cautionary tale — it's what you get when "we'll just put a logger in the language" goes uncritically. |
| **log4j 1.x.** Once the dominant model. | Eager string concat in caller code (no `{}` deferred formatting), `Logger.getLogger` static factory races, no MDC at the API level. SLF4J was created specifically because of these. |
| **log4j 2.** Strong API, strong impl, but bundled. | The API and the impl are tightly coupled. You cannot replace the impl without giving up the API. We need the impl-as-plugin property; log4j 2 doesn't preserve it cleanly. |
| **Apache Commons Logging.** Tried to be a facade. | Famously broken classloader behaviour ("the JCL nightmare"). The dynamic discovery model didn't survive the move to OSGi/modular classloaders. SLF4J's static binding fixed exactly this. |
| **Python `logging`.** Stdlib. | Hierarchical logger configuration is good; the API is verbose; no fluent builder; no markers; bound to a global root by default. Decent for Python culture; doesn't fit Ecstasy's injection-first culture. |
| **Go `log/slog` (1.21+).** Modern. | Reasonable shape. `slog` is _newer_ than SLF4J 2.x and arrived at a similar place: structured KV, levels, handlers. We could equally well copy `slog`. We don't, because (a) far fewer engineers know `slog` than know SLF4J, and (b) `slog`'s ergonomics around groups and context don't add anything an MDC + KV combo doesn't already give us. |
| **Roll our own.** Greenfield. | The space is well-trodden. Every novel decision is a decision we'd have to defend forever. The only good reason to depart from SLF4J is to fit Ecstasy's injection model — which is what we're doing for acquisition, but not for the API surface itself. |

The strongest competing argument is "Go `slog` is a cleaner design and Ecstasy is a new
language; why anchor to a Java idiom?" The answer: developer onboarding. We will pay the
cost of every developer learning a new logging API forever. We don't need to. SLF4J's
shape is in the muscle memory of probably the largest population of working backend
engineers in the world. Free familiarity is not nothing.

### 1.4 What the boundary buys us _specifically_

Concretely, here's what splitting `Logger` from `LogSink` and copying the SLF4J API
shape gets us:

- **A library author writes `@Inject Logger logger; logger.info(...)` and is done.**
  They do not pick a backend, they do not have a config file, they cannot break
  downstream consumers by their choice. A library that does logging is a library that
  does logging *politely*.
- **An application author can swap `ConsoleLogSink` for `LogbackLogSink` (future) for
  `NativeSlf4jLogSink` (future) without recompiling any library.** Behind a stable
  injection point.
- **Test authors get `MemoryLogSink` for free.** They wire it up once and assert on
  `events`. No global state, no `LogManager.getLogManager().reset()`, no flaky tests.
- **Future structured-logging consumers (log aggregators, dashboards) get a stable event
  shape.** We add a `keyValues` map to `LogEvent` once; every sink that wants to render
  it does, every sink that doesn't ignores it. No format wars.

These are not theoretical. Every Java shop that runs Logback + LogstashEncoder for
production and SimpleLogger for tests is benefiting from exactly this layering today.
We get the same property out of the box.

---

## Part 2 — Why injection (`@Inject Logger`) is the right acquisition mechanism

### 2.1 What `LoggerFactory.getLogger` actually is in Java

The Java idiom is:

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```

This is a static factory call resolved against whatever SLF4J binding is on the
classpath at startup. In a single-application JVM with one classpath, it works. In
anything more complex it gets ugly:

- **Two bindings on the classpath.** Random one wins. SLF4J prints a warning at
  startup, but nothing else.
- **Library shading.** Two libraries each shade their own SLF4J binding into their jars.
  Classloader lottery decides which wins.
- **Multi-tenant containers.** Different tenants want different log routing. Java has no
  natural way to express this; you end up plumbing tenant-scoped loggers manually or
  using `LogContext`-style hacks.
- **Tests that mutate global state.** Setting up `ListAppender` in a test means
  registering it on a logger that is global to the JVM, with cleanup discipline.

These are paper cuts in Java. They are accepted because the language has no better
option. **Ecstasy does have a better option.** The injection model is the better option.

### 2.2 What `@Inject Logger` gives us that static factories cannot

Ecstasy's `@Inject` is resolved per-container by the runtime, with a `(Type, name)`
key. Concretely, this means:

- **The host application's container chooses the sink.** A test container injects
  `MemoryLogSink`. A production container injects a configuration-driven sink. The
  embedded library has no opinion. Static `LoggerFactory.getLogger` cannot do this
  because the choice is made at JVM startup by classpath discovery.

- **Per-container override is a one-line change.** Want module X to log to a different
  destination than module Y? Provide a different Injector for module X's container.
  No bridges, no `MarkerFilter` hacks, no per-tenant `LogContext`.

- **No classpath surprises.** There is no "two bindings, who wins?" question because
  there is no classpath-level binding. The binding is a function in the suppliers map.

- **Testing is trivial.** A test sets up a child container with a `MemoryLogSink`,
  runs the system under test, asserts on captured events. No global mutation, no
  per-test `BeforeEach` cleanup of root logger appenders, no race when tests run in
  parallel.

- **Library authors don't depend on a configuration model.** A Java library that wants
  to log _to a specific destination_ in some scenarios usually adds a `setLogger(Logger)`
  method. With injection that's no longer needed; the *consumer* of the library is the
  one who decides what the library's `Logger` resolves to.

### 2.3 The `Console` precedent

Ecstasy already does this for `Console`. `@Inject Console console` is a stable contract;
the runtime swaps `TerminalConsole`, headless test consoles, captured-output consoles,
embedded-host-controlled consoles, all transparently. Logging is exactly the same
problem with the same shape: it's an output channel the platform should control on the
caller's behalf. Treating loggers like consoles is *the obvious choice once you see it*.

The alternative — "loggers are special, they need a static factory because that's what
SLF4J does" — gives up the property that motivated `Console` injection in the first
place. We would not accept that for `Console`; we should not accept it for `Logger`.

### 2.4 What about the `LoggerFactory` static escape hatch?

`lib_logging` ships `LoggerFactory` for code that genuinely cannot use injection (e.g.,
static initializers, anonymous closures with no injection scope). The escape hatch is
itself a service that consults an injected default sink — the same plumbing as
`@Inject Logger`, just exposed via a classmethod-like accessor.

This means even the escape hatch respects per-container overrides. Static is not the
same as global.

---

## Part 3 — Why the combination is greater than the sum of the parts

Now put the two together.

### 3.1 The library author's experience

Writes:
```ecstasy
@Inject Logger logger;
logger.info("processed {} records", count);
```

Reads like SLF4J. Costs nothing to learn for an SLF4J veteran. Imports nothing
backend-specific. Cannot accidentally pin a binding. Cannot leak a binding choice into
downstream consumers. Cannot affect behaviour in unrelated modules. Will keep working if
the runtime swaps the sink, the binding, the config file, the encoder, anything.

This is what every facade has aspired to, and almost-but-not-quite achieved.

### 3.2 The host application's experience

Wants production-grade logging? Provide a `LogbackLogSink` (future module). Provide it
*to the container* and every library inside the container picks it up. No
"add slf4j-logback to the classpath" ceremony, no version-skew warnings, no shading
games. The host owns the injector; the host owns the routing.

Wants logs disabled for some embedded module? Override the injection for that module's
sub-container. There is no Java equivalent that doesn't involve `MarkerFilter` hacks.

Wants a custom JSON wire format? Write a `JsonLineLogSink`, register it. Forty-plus
embedded libraries in the application start emitting JSON without one of them having
done anything.

### 3.3 The ops engineer's experience

Same story as the host: the routing decision is exactly one place — the resource
provider in the injector. There is no second place. There is no `logback.xml` somewhere
in a transitive dependency overriding the choice.

### 3.4 The test author's experience

Wires `MemoryLogSink` into the test container, runs the system under test, asserts on
`sink.events`. Test isolation is automatic because each test container has its own
suppliers map. No global root logger to reset.

This last property is, frankly, hard to overstate. Anyone who has chased a flaky CI test
caused by a `ListAppender` not being detached at the end of a previous test knows what
the alternative looks like.

---

## Part 4 — Anticipating objections

**"SLF4J is showing its age. Should we copy something newer?"**

The shape of the API is exactly what is _not_ aging. The features added in SLF4J 2.x
(KV pairs, fluent builder) are themselves modern. There is no working logging library in
mainstream use whose user-facing surface is meaningfully different *and* better. `slog`
in Go is closest, and the user-facing differences (groups, attribute encoding) are not
improvements so much as different trade-offs. None of them justify giving up SLF4J's
familiarity.

**"Why not let users pick their style — slog-like *or* SLF4J-like?"**

Two APIs is worse than one. Ecstasy is a new language; the cost of forcing one idiom is
small, and the cost of supporting two forever is large.

**"Why not lean on `@Inject Console console` and skip the whole thing?"**

`println` doesn't have levels. It doesn't have markers. It doesn't have MDC. It doesn't
have parameterized messages with deferred formatting. It doesn't have the API/impl
boundary. Building any of those on top of `Console` ad hoc, in every library that needs
them, is exactly the world before SLF4J — and we know how that turns out.

**"What about the runtime cost of the indirection?"**

The level check (`sink.isEnabled(name, level, marker)`) is the fast path. When the
level is disabled, no formatting work happens, no MDC snapshot is taken, no event is
constructed. This is the same fast-path SLF4J has and it is well-trodden.

**"Could we change our minds later?"**

Most of the API surface is in the `Logger` interface and a few small types
(`Level`, `Marker`, `MDC`, `LogEvent`, `LoggingEventBuilder`). Anything below `LogSink`
is replaceable today; anything above is replaceable only by breaking caller code.
Choosing the SLF4J shape now is the choice we want locked in. It's the choice with the
shortest list of failure modes.

---

## Summary

We're not picking SLF4J because we like Java. We're picking SLF4J because:

1. The API/impl split it pioneered is the only logging architecture that has survived
   thirty years of churn intact.
2. The specific signatures it standardized (`{}` deferred formatting, throwable
   promotion, level checks, markers, MDC, fluent KV) each solve a real problem nobody
   wants to re-solve.
3. The audience we are onboarding to Ecstasy already speaks this dialect.

We're not picking `@Inject Logger` because we like dependency injection. We're picking
it because:

1. Static `LoggerFactory.getLogger` has known failure modes (classpath races, shading
   conflicts, global state in tests) that Ecstasy's container model just doesn't
   exhibit.
2. The same property already pays for itself with `@Inject Console`. Logging is
   strictly the same shape as console output and benefits from exactly the same
   mechanism.
3. The combination — SLF4J's API surface plus per-container injection — is a logging
   library that no Java framework can quite ship, because Java doesn't have
   per-container injection at the language level. Ecstasy does. We should use it.

This is what "instantly familiar to all SLF4J users *and* better" looks like.
