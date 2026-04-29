# SLF4J 2.x Provider for a New Language (Full Guide)

This document is a **complete, copy‑paste ready** guide for implementing a minimal SLF4J 2.x backend (provider) that can initially delegate to Java, while keeping a clean path to a native runtime later.

---

## Architecture

**SLF4J (Java side)** → **Adapter (your provider)** → **RuntimeLogSink (your abstraction)** → **Backend (JUL now, native later)**

Key idea: **SLF4J is just an edge adapter**, not your core logging model.

---

## Project Layout

```
mylang-slf4j-provider/
  build.gradle.kts
  settings.gradle.kts
  src/main/java/mylang/slf4j/
    MyLangServiceProvider.java
    MyLangLoggerFactory.java
    MyLangLogger.java
    RuntimeLogSink.java
    JulRuntimeLogSink.java
    LogLevel.java
    LogEvent.java
  src/main/resources/
    META-INF/services/org.slf4j.spi.SLF4JServiceProvider
```

---

## Gradle Build

```kotlin
plugins {
    `java-library`
}

group = "mylang"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.17")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
```

---

## Service Provider

```java
package mylang.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public final class MyLangServiceProvider implements SLF4JServiceProvider {
    public static final String REQUESTED_API_VERSION = "2.0.99";

    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    @Override
    public void initialize() {
        RuntimeLogSink sink = new JulRuntimeLogSink();
        this.loggerFactory = new MyLangLoggerFactory(sink);
        this.markerFactory = new BasicMarkerFactory();
        this.mdcAdapter = new BasicMDCAdapter();
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return REQUESTED_API_VERSION;
    }
}
```

---

## Logger Factory

```java
package mylang.slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public final class MyLangLoggerFactory implements ILoggerFactory {
    private final RuntimeLogSink sink;
    private final ConcurrentMap<String, Logger> cache = new ConcurrentHashMap<>();

    public MyLangLoggerFactory(RuntimeLogSink sink) {
        this.sink = sink;
    }

    @Override
    public Logger getLogger(String name) {
        return cache.computeIfAbsent(name, n -> new MyLangLogger(n, sink));
    }
}
```

---

## Runtime Abstraction

### RuntimeLogSink

```java
package mylang.slf4j;

public interface RuntimeLogSink {
    boolean isEnabled(String loggerName, LogLevel level);
    void log(LogEvent event);
}
```

### LogLevel

```java
package mylang.slf4j;

public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
```

### LogEvent

```java
package mylang.slf4j;

public record LogEvent(
    String loggerName,
    LogLevel level,
    String message,
    Throwable throwable,
    String threadName,
    long timestampMillis
) {}
```

---

## Initial Backend (JUL)

```java
package mylang.slf4j;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class JulRuntimeLogSink implements RuntimeLogSink {

    @Override
    public boolean isEnabled(String loggerName, LogLevel level) {
        Logger logger = Logger.getLogger(loggerName);
        return logger.isLoggable(toJul(level));
    }

    @Override
    public void log(LogEvent event) {
        Logger logger = Logger.getLogger(event.loggerName());
        logger.log(
            toJul(event.level()),
            event.message(),
            event.throwable()
        );
    }

    private static Level toJul(LogLevel level) {
        return switch (level) {
            case TRACE -> Level.FINER;
            case DEBUG -> Level.FINE;
            case INFO -> Level.INFO;
            case WARN -> Level.WARNING;
            case ERROR -> Level.SEVERE;
        };
    }
}
```

---

## Logger Implementation

```java
package mylang.slf4j;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.MessageFormatter;

public final class MyLangLogger extends AbstractLogger {

    private final RuntimeLogSink sink;

    public MyLangLogger(String name, RuntimeLogSink sink) {
        this.name = name;
        this.sink = sink;
    }

    @Override
    public boolean isTraceEnabled() { return sink.isEnabled(name, LogLevel.TRACE); }

    @Override
    public boolean isDebugEnabled() { return sink.isEnabled(name, LogLevel.DEBUG); }

    @Override
    public boolean isInfoEnabled() { return sink.isEnabled(name, LogLevel.INFO); }

    @Override
    public boolean isWarnEnabled() { return sink.isEnabled(name, LogLevel.WARN); }

    @Override
    public boolean isErrorEnabled() { return sink.isEnabled(name, LogLevel.ERROR); }

    @Override public boolean isTraceEnabled(Marker m) { return isTraceEnabled(); }
    @Override public boolean isDebugEnabled(Marker m) { return isDebugEnabled(); }
    @Override public boolean isInfoEnabled(Marker m) { return isInfoEnabled(); }
    @Override public boolean isWarnEnabled(Marker m) { return isWarnEnabled(); }
    @Override public boolean isErrorEnabled(Marker m) { return isErrorEnabled(); }

    @Override
    protected String getFullyQualifiedCallerName() {
        return MyLangLogger.class.getName();
    }

    @Override
    protected void handleNormalizedLoggingCall(
        Level level,
        Marker marker,
        String messagePattern,
        Object[] arguments,
        Throwable throwable
    ) {
        String msg = MessageFormatter
            .arrayFormat(messagePattern, arguments, throwable)
            .getMessage();

        sink.log(new LogEvent(
            name,
            map(level),
            msg,
            throwable,
            Thread.currentThread().getName(),
            System.currentTimeMillis()
        ));
    }

    private static LogLevel map(Level level) {
        return switch (level) {
            case TRACE -> LogLevel.TRACE;
            case DEBUG -> LogLevel.DEBUG;
            case INFO -> LogLevel.INFO;
            case WARN -> LogLevel.WARN;
            case ERROR -> LogLevel.ERROR;
        };
    }
}
```

---

## Service Registration

File:

```
META-INF/services/org.slf4j.spi.SLF4JServiceProvider
```

Content:

```
mylang.slf4j.MyLangServiceProvider
```

---

## Example Usage

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

---

## Design Notes

- Use `AbstractLogger` → avoids implementing 40+ overloads manually
- Use `MessageFormatter` → correct `{}` formatting
- Ignore markers initially
- Use `BasicMDCAdapter` unless you need distributed tracing

---

## Future Evolution

Replace:

```
JulRuntimeLogSink
```

with:

```
NativeRuntimeLogSink
```

No SLF4J changes required.

---

## Core Principle

**Do NOT design your runtime around SLF4J.**

Instead:

- Define your own logging model
- Treat SLF4J as an adapter layer
- Keep full control over your logging semantics

---

End of document.
