package org.xvm.asm;


import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;


/**
 * An {@link ErrorListener} that emits a JFR event per diagnostic on its way to the listener it
 * wraps.
 *
 * <p>A decorator, like {@link Slf4jErrorListener}: the wrapped listener still decides
 * {@link #isAbortDesired}, still collects, and still drives the compiler's stages.
 *
 * <h2>How this relates to {@code XtcEngine}'s events</h2>
 *
 * <p>It complements them rather than replacing them, because they are different granularities.
 * {@code XtcEngine.CompileEvent} is a <em>span</em>: it opens before a compile, commits after, and
 * records how long the whole operation took and how many modules came out. A listener cannot
 * produce that - it never learns when a compile begins or what it produced, only that a diagnostic
 * happened. Conversely a span cannot tell you which diagnostics occurred inside it. Recording both
 * gives a JFR profile the shape it wants: spans to see where time went, per-diagnostic events to
 * see what happened inside one.
 *
 * <h2>Cost when nobody is recording</h2>
 *
 * <p>{@link Event#shouldCommit()} answers false when no recording has this event enabled, so the
 * guard below means an unrecorded diagnostic costs the allocation of one short-lived event object
 * and a check. Populating the fields - which calls {@link ErrorInfo#getMessage()}, and so formats
 * the message from its code and parameters - happens only when something is actually listening.
 */
public class JfrErrorListener
        implements ErrorListener {
    private final @NotNull ErrorListener delegate;

    /**
     * @param delegate  the listener to pass everything on to
     */
    public JfrErrorListener(@NotNull ErrorListener delegate) {
        this.delegate = requireNonNull(delegate, "delegate");
    }

    /**
     * @return the listener this one wraps
     */
    public @NotNull ErrorListener getDelegate() {
        return delegate;
    }

    @Override
    public void log(ErrorInfo err) {
        var event = new DiagnosticEvent();
        if (event.shouldCommit()) {
            // only now is the message worth formatting
            event.severity = err.getSeverity().name();
            event.code     = err.getCode();
            event.message  = err.getMessage();
            event.commit();
        }
        delegate.log(err);
    }

    @Override
    public boolean isAbortDesired() {
        return delegate.isAbortDesired();
    }

    @Override
    public boolean hasSeriousErrors() {
        return delegate.hasSeriousErrors();
    }

    @Override
    public boolean isSilent() {
        return delegate.isSilent();
    }

    @Override
    public String toString() {
        return "JFR -> " + delegate;
    }

    /**
     * One diagnostic, as a JFR event.
     *
     * <p>{@code @StackTrace(false)}: the stack at the point a diagnostic is logged is the compiler's
     * own call chain, which says nothing about the source being compiled and is the expensive part
     * of an event. The code and message are what identify it.
     */
    @Name("org.xvm.Diagnostic")
    @Label("XTC Diagnostic")
    @Category({"Ecstasy", "Diagnostics"})
    @StackTrace(false)
    static final class DiagnosticEvent
            extends Event {
        @Label("Severity") String severity;
        @Label("Code")     String code;
        @Label("Message")  String message;
    }
}
