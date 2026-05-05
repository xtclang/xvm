# `@Inject Logger` end-to-end — a working example

This document shows the canonical way an Ecstasy application uses `lib_logging`. The
goal is to be the file someone reads when they ask "okay, but what does it actually
look like in real code?"

The accompanying executable sample lives at
[`manualTests/src/main/x/TestLogger.x`](../../../manualTests/src/main/x/TestLogger.x).

> **Status (2026-05).** Runtime injection of `@Inject Logger logger;` is wired for
> the manual interpreter demo. The injected value is a `BasicLogger`, so per-fiber
> `MDC` survives injection. This POC keeps the public API independent of the temporary
> runtime wiring.

## API at a glance

There are exactly two acquisition shapes:

```ecstasy
@Inject Logger logger;                        // root logger via injection
Logger payments = logger.named("payments");   // per-name child via the API
```

There is **no** `@Inject("payments") Logger logger;` form. Per-name loggers are
*derived* from the injected one, the same way SLF4J users write
`LoggerFactory.getLogger(MyClass.class)` once at the top of a class.

The examples below `import` the public lib_logging types unqualified by adding
type-level imports next to the `package log import …` alias:

```ecstasy
package log import logging.xtclang.org;
import log.Logger;
import log.MarkerFactory;
import log.MDC;
import log.Marker;
```

You can also keep the `log.` prefix everywhere — both forms compile — but unqualified
reads better in real code, and that's the form below.

## The smallest possible app

```ecstasy
module HelloLogging {
    package log import logging.xtclang.org;
    import log.Logger;

    void run() {
        @Inject Logger logger;
        logger.info("hello, {}", ["world"]);
    }
}
```

That's it. There is no factory call, no static initializer, no configuration file. The
runtime container hands the application a `Logger` (named `"logger"` for now — see
Stage 4 of the runtime plan for the optional compiler-side default-name change); the
default sink (`ConsoleLogSink`) emits the line to the platform `Console`. Output:

```
2026-04-29T11:23:45.012Z [] INFO  logger: hello, world
```

## A more realistic app

The example below is closer to what real code looks like. It demonstrates:
- a *named* logger acquired by injection;
- per-level emission with parameterized messages;
- attaching an exception via the `cause` parameter;
- using a marker for categorical routing;
- using MDC for request-scoped context;
- the SLF4J 2.x fluent event builder.

```ecstasy
module PaymentService {
    package log import logging.xtclang.org;
    import log.Logger;
    import log.MarkerFactory;
    import log.MDC;
    import log.Marker;

    @Inject Logger        logger;
    @Inject MarkerFactory markers;
    @Inject MDC           mdc;

    Marker audit = markers.getMarker("AUDIT");

    void run() {
        processPayment("p_123", 4200, "EUR", "user_42");
    }

    void processPayment(String paymentId, Int amount, String currency, String userId) {
        // Per-request context that should appear on every event below.
        mdc.put("paymentId", paymentId);
        mdc.put("user",      userId);

        try {
            logger.info("processing payment of {} {}", [amount, currency]);

            validate(paymentId, amount);
            charge(paymentId, amount, currency);

            // Structured + categorical: tag with AUDIT, attach typed KV pairs.
            logger.atInfo()
                  .addMarker(audit)
                  .addKeyValue("amount",   amount)
                  .addKeyValue("currency", currency)
                  .log("payment completed");

        } catch (Exception e) {
            logger.error("payment failed: {}", [paymentId], cause=e);
        } finally {
            mdc.remove("paymentId");
            mdc.remove("user");
        }
    }

    void validate(String paymentId, Int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException($"amount must be positive: {amount}");
        }
        if (logger.debugEnabled) {
            logger.debug("validated {} (amount={})", [paymentId, amount]);
        }
    }

    void charge(String paymentId, Int amount, String currency) {
        // ... (calls into payment-processor service)
    }
}
```

When run with the default `ConsoleLogSink` and root level `Info`, this prints (for a
successful payment):

```
2026-04-29T11:23:45.012Z [main] INFO  PaymentService: processing payment of 4200 EUR
2026-04-29T11:23:45.034Z [main] INFO  PaymentService: payment completed [marker=AUDIT]
```

The `validate` call's `debug` line is elided because `Debug < Info`, and crucially the
`logger.debugEnabled` guard keeps the parameter array `[paymentId, amount]` from being
constructed at all.

## Scaling up — multiple loggers, one per module/class

In a typical app each scope just has its own `logger` — inject once, use directly.
You only reach for `Logger.named(String)` when you need the *same* enclosing scope to
emit under multiple names. The most common case is one logger per class within a
module:

```ecstasy
module BillingService {
    package log import logging.xtclang.org;
    import log.Logger;

    @Inject Logger logger;  // injected once for the whole module

    service Invoicer {
        Logger logger = BillingService.logger.named("billing.Invoicer");

        void issue(Invoice inv) {
            logger.info("issuing invoice {}", [inv.id]);
            // ...
        }
    }

    service Charger {
        Logger logger = BillingService.logger.named("billing.Charger");

        void charge(Invoice inv) {
            logger.info("charging {} for {}", [inv.customer, inv.total]);
            // ...
        }
    }
}
```

Each service holds its own `logger` field — call sites still read `logger.info(...)`,
no rename. Both share the injected one's sink, so a configuration-driven sink
(`lib_logging_logback`, future) can set `billing.Charger` to `Debug` while keeping
`billing.Invoicer` at `Info`. This matches SLF4J's `LoggerFactory.getLogger(MyClass)`
idiom one-to-one.

## Without injection — `LoggerFactory`

For static initialisers or anywhere injection isn't in scope:

```ecstasy
module Util {
    package log import logging.xtclang.org;

    static log.Logger logger = log.LoggerFactory.getLogger("util.Helper");

    static void greet(String name) {
        logger.info("hello, {}", [name]);
    }
}
```

`LoggerFactory.getLogger` is itself a service that consults an injected default
`LogSink`, so the per-container override property still holds.

## Without injection — explicit construction

Anywhere injection isn't in scope (static initialisers, unit-test fixtures), construct
the logger by hand exactly like the unit tests do:

```ecstasy
module Today {
    package log import logging.xtclang.org;
    import log.BasicLogger;
    import log.ConsoleLogSink;
    import log.Logger;
    import log.LogSink;

    void run() {
        LogSink sink   = new ConsoleLogSink();
        Logger  logger = new BasicLogger("Today", sink);

        logger.info("hello, {}", ["world"]);
    }
}
```

`LoggerFactory.getLogger(...)` is also available for the static-init case.

## Cheat sheet

| You want to… | Write |
|---|---|
| Inject the root logger | `@Inject Logger logger;` |
| Get a per-name logger | `Logger payments = logger.named("payments");` |
| Log info | `logger.info("msg")` |
| Log info with args | `logger.info("msg {}", [arg])` |
| Log info with exception | `logger.info("msg", cause=e)` |
| Log info with marker | `logger.info("msg", marker=AUDIT)` |
| Cheap level check | `if (logger.infoEnabled) { ... }` |
| Fluent builder | `logger.atInfo().addMarker(AUDIT).log("msg")` |
| Get a logger by name (no injection) | `LoggerFactory.getLogger("foo")` |
| Attach context for this request | `mdc.put("requestId", id);` |

That covers ~95% of real-world logging use.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
