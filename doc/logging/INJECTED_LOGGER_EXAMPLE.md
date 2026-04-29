# `@Inject Logger` end-to-end — a working example

This document shows the canonical way an Ecstasy application uses `lib_logging`. The
goal is to be the file someone reads when they ask "okay, but what does it actually
look like in real code?"

The accompanying executable sample lives at
[`manualTests/src/main/x/TestLogger.x`](../../manualTests/src/main/x/TestLogger.x).

> **Status reminder.** The runtime injection plumbing (the native side that resolves
> `@Inject Logger logger;` to a real `BasicLogger` instance) is not yet wired up — see
> `OPEN_QUESTIONS.md`. The example below is the shape user code is *meant* to take.
> Until the runtime is wired, equivalent behaviour can be obtained by constructing
> `BasicLogger` directly against any `LogSink`, exactly as the unit tests under
> `lib_logging/src/test/x/LoggingTest/` do.

## The smallest possible app

```ecstasy
module HelloLogging {
    package log import logging.xtclang.org;

    void run() {
        @Inject log.Logger logger;
        logger.info("hello, {}", ["world"]);
    }
}
```

That's it. There is no factory call, no static initializer, no configuration file. The
runtime container hands the application a `Logger`; the default sink (`ConsoleLogSink`)
emits the line to the platform `Console`. Output:

```
2026-04-29T11:23:45.012Z [main] INFO  HelloLogging: hello, world
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

    @Inject("PaymentService") log.Logger    logger;
    @Inject                   log.MarkerFactory markers;
    @Inject                   log.MDC           mdc;

    log.Marker AUDIT = markers.getMarker("AUDIT");

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
                  .addMarker(AUDIT)
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

The SLF4J convention is one logger per class, named by the class. In Ecstasy the
equivalent is one named injection per logical scope:

```ecstasy
module BillingService {
    package log import logging.xtclang.org;

    service Invoicer {
        @Inject("billing.Invoicer") log.Logger logger;

        void issue(Invoice inv) {
            logger.info("issuing invoice {}", [inv.id]);
            // ...
        }
    }

    service Charger {
        @Inject("billing.Charger") log.Logger logger;

        void charge(Invoice inv) {
            logger.info("charging {} for {}", [inv.customer, inv.total]);
            // ...
        }
    }
}
```

Two loggers, two names. A configuration-driven sink (`lib_logging_logback`, future) can
set `billing.Charger` to `Debug` while keeping `billing.Invoicer` at `Info`.

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

## Without the runtime wiring (today's reality)

Until the runtime side resolves `@Inject Logger`, you can do the same thing by hand:

```ecstasy
module Today {
    package log import logging.xtclang.org;

    void run() {
        @Inject Console console;
        log.LogSink sink   = new log.ConsoleLogSink();
        log.Logger  logger = new log.BasicLogger("Today", sink);

        logger.info("hello, {}", ["world"]);
    }
}
```

This is exactly what the unit tests in `lib_logging/src/test/x/LoggingTest/` do, and
exactly what the manualTest does today. Once `RTLogger.java` lands in
`javatools_jitbridge` and is registered in `nMainInjector`, the explicit construction
becomes unnecessary and the code in the earlier sections is what callers will actually
write.

## Cheat sheet

| You want to… | Write |
|---|---|
| Log info | `logger.info("msg")` |
| Log info with args | `logger.info("msg {}", [arg])` |
| Log info with exception | `logger.info("msg", cause=e)` |
| Log info with marker | `logger.info("msg", marker=AUDIT)` |
| Cheap level check | `if (logger.infoEnabled) { ... }` |
| Fluent builder | `logger.atInfo().addMarker(AUDIT).log("msg")` |
| Get a logger by name | `@Inject("foo") Logger logger;` |
| Get a logger by class | `LoggerFactory.getLogger(MyClass)` |
| Attach context for this request | `mdc.put("requestId", id);` |

That covers ~95% of real-world logging use.
