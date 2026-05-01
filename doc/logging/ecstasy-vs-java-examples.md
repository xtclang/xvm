# Ecstasy `lib_logging` vs Java SLF4J — side by side

For every API in `lib_logging` this document shows a working SLF4J 2.x example in Java
first, then the equivalent in Ecstasy. The point is to make adoption frictionless: anyone
who has used SLF4J should be able to skim once and be productive.

## 1. Hello world

**Java (SLF4J):**

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Demo {
    private static final Logger log = LoggerFactory.getLogger(Demo.class);

    public static void main(String[] args) {
        log.info("Hello {}", "world");
    }
}
```

**Ecstasy (`lib_logging`):**

```ecstasy
module Demo {
    package log import logging.xtclang.org;

    void run() {
        @Inject log.Logger logger;
        logger.info("Hello {}", ["world"]);
    }
}
```

The SLF4J static-factory pattern still works in Ecstasy:

```ecstasy
@Inject log.LoggerFactory factory;
log.Logger logger = factory.getLogger(Demo);
```

## 2. Level checks (avoid expensive arg construction)

**Java:**

```java
if (log.isDebugEnabled()) {
    log.debug("computed result: {}", expensiveSerialize(thing));
}
```

**Ecstasy:**

```ecstasy
if (logger.debugEnabled) {
    logger.debug("computed result: {}", [expensiveSerialize(thing)]);
}
```

## 3. Logging an exception

**Java:**

```java
try {
    process(req);
} catch (Exception e) {
    log.error("processing failed for {}", req.id(), e);
}
```

**Ecstasy:**

```ecstasy
try {
    process(req);
} catch (Exception e) {
    logger.error("processing failed for {}", [req.id], cause=e);
}
```

Both follow the SLF4J **throwable-promotion rule**: a trailing `Exception`/`Throwable`
that has no matching placeholder is treated as the cause, not as a substitution.

## 4. Markers

**Java:**

```java
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");
log.info(AUDIT, "user {} signed in", userId);
```

**Ecstasy:**

```ecstasy
@Inject log.MarkerFactory markers;
log.Marker AUDIT = markers.getMarker("AUDIT");
logger.info("user {} signed in", [userId], marker=AUDIT);
```

## 5. MDC

**Java:**

```java
import org.slf4j.MDC;

MDC.put("requestId", req.id());
try {
    log.info("handling request");
    handle(req);
} finally {
    MDC.remove("requestId");
}
```

**Ecstasy:**

```ecstasy
@Inject log.MDC mdc;

mdc.put("requestId", req.id);
try {
    logger.info("handling request");
    handle(req);
} finally {
    mdc.remove("requestId");
}
```

## 6. Fluent event builder (SLF4J 2.x)

**Java:**

```java
log.atInfo()
   .addMarker(AUDIT)
   .addKeyValue("requestId", req.id())
   .addKeyValue("user", userId)
   .setCause(e)
   .log("payment {} failed for {}", paymentId, customer);
```

**Ecstasy:**

```ecstasy
logger.atInfo()
      .addMarker(AUDIT)
      .addKeyValue("requestId", req.id)
      .addKeyValue("user", userId)
      .setCause(e)
      .log("payment {} failed for {}", paymentId, customer);
```

When the level is disabled, both implementations short-circuit to a no-op builder so the
`addArgument` / `addKeyValue` calls are free.

## 7. Acquiring a logger by name vs by class

**Java:**

```java
Logger byName  = LoggerFactory.getLogger("com.example.foo");
Logger byClass = LoggerFactory.getLogger(MyClass.class);
```

**Ecstasy:**

```ecstasy
@Inject log.LoggerFactory factory;
log.Logger byName  = factory.getLogger("com.example.foo");
log.Logger byClass = factory.getLogger(MyClass);

// Or, by injection:
@Inject("com.example.foo") log.Logger logger;
```

## 8. Configuration: changing the root level

**Java (`logback.xml`):**

```xml
<configuration>
  <root level="DEBUG">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

**Java (programmatic, Logback):**

```java
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.DEBUG);
```

**Ecstasy (default `ConsoleLogSink`):**

```ecstasy
@Inject log.ConsoleLogSink sink;
sink.setRootLevel(log.Debug);
```

For a richer per-logger configuration tree see `logback-integration.md` — the future
`lib_logging_logback` module would expose a programmatic and/or file-based config API
analogous to Logback's `JoranConfigurator`.

## 9. Custom appender / sink

**Java (Logback):**

```java
public class CountingAppender extends AppenderBase<ILoggingEvent> {
    private final AtomicLong count = new AtomicLong();

    @Override
    protected void append(ILoggingEvent e) {
        count.incrementAndGet();
    }

    public long getCount() { return count.get(); }
}
```

Wire it via `logback.xml`:

```xml
<appender name="COUNT" class="com.example.CountingAppender"/>
<root level="INFO"><appender-ref ref="COUNT"/></root>
```

**Ecstasy (`lib_logging`):**

```ecstasy
service CountingSink
        implements log.LogSink {

    public/private Int count = 0;

    @Override
    Boolean isEnabled(String name, log.Level level, log.Marker? marker = Null) = True;

    @Override
    void log(log.LogEvent event) {
        ++count;
    }
}
```

Wire it by replacing the injected default sink — see `custom-sinks.md` for the runtime
side of the story.

## 10. NOP / silent logger (for libraries that opt out)

**Java:**

```java
// add slf4j-nop to the classpath, OR:
Logger silent = NOPLogger.NOP_LOGGER;
```

**Ecstasy:**

```ecstasy
log.LogSink silent = new log.NoopLogSink();
log.Logger  logger = new log.BasicLogger("com.example", silent);
```

## 11. Capturing events in a test

**Java (Logback `ListAppender`):**

```java
ListAppender<ILoggingEvent> capture = new ListAppender<>();
capture.start();
((Logger) LoggerFactory.getLogger("com.example")).addAppender(capture);

systemUnderTest.run();

assertThat(capture.list)
    .hasSize(1)
    .extracting(ILoggingEvent::getFormattedMessage)
    .containsExactly("processed 42 records");
```

**Ecstasy (`MemoryLogSink`):**

```ecstasy
log.MemoryLogSink capture = new log.MemoryLogSink();
log.Logger logger = new log.BasicLogger("com.example", capture);

systemUnderTest.run(logger);

assert capture.events.size == 1;
assert capture.events[0].message == "processed 42 records";
```

## 12. Throwable promotion

**Java:**

```java
log.warn("cleanup failed", new IOException("disk full"));
// SLF4J knows the Throwable is the cause, not a {} substitution.
```

**Ecstasy:**

```ecstasy
logger.warn("cleanup failed", cause=new IOException("disk full"));
```

The `MessageFormatter.format` method also enforces the same rule: if the last entry of
`arguments` is an `Exception` and there is no matching placeholder, it is promoted to
the cause.

## 13. Parameterized message edge cases

**Java:**

```java
log.info("{} of {}", current, total);                  // both filled
log.info("plain message", arg);                        // arg ignored — no {}
log.info("{} {} {}", "a", "b");                        // last {} stays literal
log.info("escaped \\{}", "x");                         // emits "escaped {}"
```

**Ecstasy:**

```ecstasy
logger.info("{} of {}", [current, total]);
logger.info("plain message", [arg]);
logger.info("{} {} {}", ["a", "b"]);
logger.info("escaped \\{}", ["x"]);
```

The semantics are the same — `MessageFormatter` is a port of SLF4J's reference behaviour.


---

_See also [README.md](README.md) for the full doc index and reading paths._
