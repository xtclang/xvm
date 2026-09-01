package org.xvm.asm;


import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;


/**
 * An {@link ErrorListener} that mirrors every diagnostic to an slf4j {@link Logger} on its way to
 * the listener it wraps.
 *
 * <p>This is a decorator, not a replacement: the wrapped listener still decides
 * {@link #isAbortDesired}, still collects, and still drives the compiler's stages. Logging observes;
 * it does not participate. Constructing one of these and handing it to
 * {@code XtcEngine.compile(ErrorListener, ...)} is enough to get the whole pipeline's diagnostics
 * into a host's logging, because the listener is reached by ownership rather than found in ambient
 * state.
 *
 * <h2>Cost when the level is disabled</h2>
 *
 * <p>The point of routing through slf4j rather than formatting strings is that a disabled level
 * costs a boolean read. Two rules make that true, and both are easy to get wrong:
 *
 * <ul>
 *   <li>guard with {@code isXxxEnabled()} before doing any work that only exists for the log -
 *       here, before calling {@link ErrorInfo#getMessage()}, which formats the message from its
 *       code and parameters;</li>
 *   <li>pass the arguments to slf4j as parameters ({@code log.trace("{} {}", a, b)}) rather than
 *       concatenating, so nothing is built when the level is off.</li>
 * </ul>
 *
 * <p>{@link #trace} shows the near-zero-cost form for the hot path: the message is never
 * constructed, and no object is allocated, unless TRACE is actually on.
 */
public class Slf4jErrorListener
        implements ErrorListener {
    private final @NotNull ErrorListener delegate;
    private final Logger        logger;

    /**
     * @param delegate  the listener to pass everything on to
     */
    public Slf4jErrorListener(@NotNull ErrorListener delegate) {
        this(delegate, LoggerFactory.getLogger(Slf4jErrorListener.class));
    }

    /**
     * @param delegate  the listener to pass everything on to
     * @param logger    the logger to mirror to
     */
    public Slf4jErrorListener(@NotNull ErrorListener delegate, @NotNull Logger logger) {
        this.delegate = requireNonNull(delegate, "delegate");
        this.logger   = requireNonNull(logger, "logger");
    }

    /**
     * @return the listener this one wraps
     */
    public @NotNull ErrorListener getDelegate() {
        return delegate;
    }

    @Override
    public void log(ErrorInfo err) {
        mirror(err);
        delegate.log(err);
    }

    /**
     * Trace a step that is not a diagnostic, at near-zero cost when TRACE is off.
     *
     * <p>This is the shape to copy for hot-path tracing. When TRACE is disabled the call costs one
     * boolean read: {@code detail} is passed by reference, no message is formatted, and nothing is
     * allocated. Written as {@code logger.trace("resolving " + type + " for " + ctx)} instead, the
     * concatenation happens on every call whether or not anyone is listening.
     *
     * @param what    what is happening; a constant, not a built string
     * @param detail  the detail to interpolate, formatted only if TRACE is on
     */
    public void trace(String what, Object detail) {
        if (logger.isTraceEnabled()) {
            logger.trace("{}: {}", what, detail);
        }
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
        return "Slf4j -> " + delegate;
    }

    /**
     * Mirror one diagnostic at the level its severity maps to, doing no work if that level is off.
     */
    private void mirror(ErrorInfo err) {
        switch (err.getSeverity()) {
        case FATAL, ERROR -> {
            if (logger.isErrorEnabled()) {
                logger.error("[{}] {}", err.getCode(), err.getMessage());
            }
        }
        case WARNING -> {
            if (logger.isWarnEnabled()) {
                logger.warn("[{}] {}", err.getCode(), err.getMessage());
            }
        }
        case INFO -> {
            if (logger.isInfoEnabled()) {
                logger.info("[{}] {}", err.getCode(), err.getMessage());
            }
        }
        default -> {
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] {}", err.getCode(), err.getMessage());
            }
        }
        }
    }
}
