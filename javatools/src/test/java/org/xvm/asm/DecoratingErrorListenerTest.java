package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.slf4j.helpers.AbstractLogger;

import org.xvm.compiler.Compiler;
import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for the listener decorators - {@link Slf4jErrorListener} and {@link JfrErrorListener}.
 *
 * <p>The wiring is the easy half. What these mostly pin is the part that is easy to get wrong and
 * silently expensive: that a disabled log level does no work, and that the decorator does not
 * disturb the semantics of the listener it wraps.
 */
public class DecoratingErrorListenerTest {
    @Test
    public void slf4jMirrorsEverythingAndStillDelegates() {
        var collected = new ErrorList(10);
        var log       = new RecordingLogger(true);
        var listener  = new Slf4jErrorListener(collected, log);

        listener.log(info(Severity.ERROR,   Compiler.FATAL_ERROR));
        listener.log(info(Severity.WARNING, Compiler.FATAL_ERROR));
        listener.log(info(Severity.INFO,    Compiler.FATAL_ERROR));

        assertEquals(3, log.lines.size(), "every diagnostic is mirrored");
        assertTrue(log.lines.get(0).startsWith("ERROR"), () -> log.lines.get(0));
        assertTrue(log.lines.get(1).startsWith("WARN"),  () -> log.lines.get(1));
        assertTrue(log.lines.get(2).startsWith("INFO"),  () -> log.lines.get(2));

        assertEquals(3, collected.getErrors().size(), "and still reaches the wrapped listener");
        assertTrue(collected.hasSeriousErrors(), "whose semantics are unchanged");
    }

    /**
     * The reason to route through slf4j rather than build strings is that a disabled level costs a
     * boolean read. If the decorator formatted first and asked afterwards, this would fail.
     */
    @Test
    public void aDisabledLevelFormatsNothing() {
        var formats  = new AtomicInteger();
        var listener = new Slf4jErrorListener(ErrorListener.BLACKHOLE, new RecordingLogger(false));

        listener.log(new CountingErrorInfo(Severity.ERROR, Compiler.FATAL_ERROR, formats));

        assertEquals(0, formats.get(),
                "getMessage() must not be called when the level is off - that is the whole point");
    }

    @Test
    public void traceCostsNothingWhenTraceIsOff() {
        var renders  = new AtomicInteger();
        var listener = new Slf4jErrorListener(ErrorListener.BLACKHOLE, new RecordingLogger(false));

        // toString() stands in for "work done only for the log"
        listener.trace("resolving", new Object() {
            @Override public String toString() {
                renders.incrementAndGet();
                return "expensive";
            }
        });

        assertEquals(0, renders.get(), "a disabled TRACE must not render its argument");
    }

    @Test
    public void jfrStillDelegatesWhetherOrNotAnyoneIsRecording() {
        var collected = new ErrorList(10);
        var listener  = new JfrErrorListener(collected);

        listener.log(info(Severity.ERROR, Compiler.FATAL_ERROR));
        listener.log(info(Severity.WARNING, Compiler.FATAL_ERROR));

        assertEquals(2, collected.getErrors().size(),
                "the wrapped listener sees everything with no recording running");
        assertTrue(collected.hasSeriousErrors());
    }

    /**
     * A decorator must not answer these itself - the wrapped listener owns the abort and
     * serious-error semantics that drive the compiler's stages.
     */
    @Test
    public void decoratorsDoNotDisturbTheWrappedSemantics() {
        var strict = ErrorList.firstError();
        strict.log(info(Severity.ERROR, Compiler.FATAL_ERROR));
        assertTrue(strict.isAbortDesired(), "one error is enough for a firstError() list");

        assertTrue(new Slf4jErrorListener(strict, new RecordingLogger(true)).isAbortDesired());
        assertTrue(new JfrErrorListener(strict).isAbortDesired());
        assertTrue(new Slf4jErrorListener(strict, new RecordingLogger(true)).hasSeriousErrors());
        assertTrue(new JfrErrorListener(strict).hasSeriousErrors());

        var silent = ErrorListener.BLACKHOLE;
        assertTrue(new JfrErrorListener(silent).isSilent(), "silence is the wrapped listener's");
    }

    @Test
    public void decoratorsRejectANullDelegate() {
        assertThrows(NullPointerException.class, () -> new JfrErrorListener(null));
        assertThrows(NullPointerException.class,
                () -> new Slf4jErrorListener(null, new RecordingLogger(true)));
        assertThrows(NullPointerException.class,
                () -> new Slf4jErrorListener(ErrorListener.BLACKHOLE, null));
    }

    @Test
    public void decoratorsCompose() {
        var collected = new ErrorList(10);
        var stacked   = new JfrErrorListener(new Slf4jErrorListener(collected, new RecordingLogger(true)));

        stacked.log(info(Severity.ERROR, Compiler.FATAL_ERROR));

        assertEquals(1, collected.getErrors().size(), "both decorators pass it on");
        assertSame(collected,
                ((Slf4jErrorListener) ((JfrErrorListener) stacked).getDelegate()).getDelegate());
    }

    private static ErrorListener.ErrorInfo info(Severity severity, String code) {
        return new ErrorListener.ErrorInfo(severity, code, null, (XvmStructure) null);
    }

    /**
     * An ErrorInfo that counts how often its message is formatted, so a test can prove the work did
     * not happen.
     */
    private static final class CountingErrorInfo
            extends ErrorListener.ErrorInfo {
        private final AtomicInteger counter;

        CountingErrorInfo(Severity severity, String code, AtomicInteger counter) {
            super(severity, code, null, (XvmStructure) null);
            this.counter = counter;
        }

        @Override
        public String getMessage() {
            counter.incrementAndGet();
            return super.getMessage();
        }
    }

    /**
     * A minimal slf4j Logger that records what it was asked to write and can report every level as
     * disabled, so the "no work when off" claim is testable without a logging backend.
     */
    private static final class RecordingLogger
            extends AbstractLogger {
        /** Never Java-serialized; AbstractLogger is Serializable, this is a test double. */
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("serial")  // never serialized; see above
        final List<String>    lines = new ArrayList<>();
        private final boolean enabled;

        RecordingLogger(boolean enabled) {
            this.enabled = enabled;
        }

        @Override public boolean isTraceEnabled()                       { return this.enabled; }
        @Override public boolean isDebugEnabled()                       { return this.enabled; }
        @Override public boolean isInfoEnabled()                        { return this.enabled; }
        @Override public boolean isWarnEnabled()                        { return this.enabled; }
        @Override public boolean isErrorEnabled()                       { return this.enabled; }
        @Override public boolean isTraceEnabled(org.slf4j.Marker m)     { return this.enabled; }
        @Override public boolean isDebugEnabled(org.slf4j.Marker m)     { return this.enabled; }
        @Override public boolean isInfoEnabled(org.slf4j.Marker m)      { return this.enabled; }
        @Override public boolean isWarnEnabled(org.slf4j.Marker m)      { return this.enabled; }
        @Override public boolean isErrorEnabled(org.slf4j.Marker m)     { return this.enabled; }

        @Override protected String getFullyQualifiedCallerName()        { return getClass().getName(); }

        @Override
        protected void handleNormalizedLoggingCall(org.slf4j.event.Level level, org.slf4j.Marker marker,
                String format, Object[] args, Throwable t) {
            lines.add(level + " " + format);
        }
    }
}
