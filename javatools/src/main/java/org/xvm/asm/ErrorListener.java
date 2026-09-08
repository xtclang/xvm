package org.xvm.asm;


import java.lang.management.ManagementFactory;

import java.text.MessageFormat;

import java.util.Arrays;
import java.util.ResourceBundle;

import org.xvm.compiler.Source;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.copyOf;

import org.jetbrains.annotations.NotNull;


/**
 * A listener for errors being reported about source code, compilation, assembly, or verification of
 * XVM structures.
 */
@FunctionalInterface
/*
 * TODO: this interface is the single sink every diagnostic in the compiler and runtime now flows
 *       through, so it is where the following belong. Each is a DECORATOR - wrap a listener, pass
 *       everything on, and do the extra work on the way past - not a change to this interface:
 *
 *       - log sinks (slf4j and friends). See Slf4jErrorListener in this package for the shape;
 *         the mapping that matters is Severity -> log level, and the guard that matters is
 *         isXxxEnabled() so a disabled level costs a boolean read.
 *
 *       - JFR events. See JfrErrorListener. JFR is already used by XtcEngine, so no dependency is
 *         involved, and an Event that is not being recorded costs a shouldCommit() check.
 *
 *       - LSP diagnostics. XtcEngine.compile(ErrorListener, ...) already hands a host its own sink
 *         and TeeErrorListener already shows how to observe without disturbing the primary's
 *         abort/serious-error semantics, which is exactly what an LSP needs.
 *
 *       WHAT WRAPPING ACTUALLY CAPTURES - stated precisely, because the obvious claim is wrong.
 *       The listener is reached by OWNERSHIP: ConstantPool for compile-time work, Container for
 *       runtime work. So wrapping the listener handed to XtcEngine.compile captures everything
 *       owned by THAT pool - the module being compiled, its stages, its metadata. It does NOT
 *       capture work owned by a different pool. Every FileStructure builds its own ConstantPool,
 *       so work owned by a library pool reports through THAT pool -> ErrorListener.RUNTIME, and the
 *       caller never hears it. (The no-argument `ensureTypeInfo()` that made this reachable from
 *       ordinary compiler code is since deleted; what remains are the soft asserts that log through
 *       the owning pool on purpose.)
 *
 *       That is a real hole for a host that wants every diagnostic, and the fix is not a shared
 *       library listener - that would put back the mutable shared state this replaced, and would
 *       make two parallel compiles fight over one field. The fix is that "who is asking" must beat
 *       "who owns the constant": pass the caller's listener to ensureTypeInfo instead of letting
 *       the no-argument overload consult the pool. 75 call sites still use that overload; see E35
 *       step D in the enhancement list. The pool default is a sound FALLBACK, but it should never
 *       win over an explicit caller.
 *
 *       What IS newly true is that wrapping one owner captures everything downstream OF THAT OWNER.
 *       That was not true at all while half the paths defaulted to BLACKHOLE or consulted a mutable
 *       field on FileStructure - there was no single place to wrap.
 *
 *       Note also that a decorator only has to forward log(); it does NOT have to think about
 *       control flow, because log() no longer carries any. Whether to stop is asked separately,
 *       through isAbortDesired(), which a decorator delegates to the listener it wraps.
 */
public interface ErrorListener {
    // ----- API -----------------------------------------------------------------------------------

    /**
     * Record a diagnostic.
     *
     * <p>Recording only. Whether the process that reported the diagnostic should stop is a separate
     * question, asked separately, through {@link #isAbortDesired()}.
     *
     * <p>This used to return a boolean meaning "abort", which conflated observing with
     * participating and left a host with no correct value to return: {@code false} suppressed a
     * legitimate abort, {@code true} invented one. Making it {@code void} is what makes an observer
     * impossible to get wrong - a lambda that only wants to watch now simply watches.
     *
     * <p><b>An implementation must tolerate concurrent calls.</b> A host passes one listener to an
     * engine that compiles modules in parallel, so the sink at the end of the chain is shared by
     * construction - it is not something a caller can arrange its way out of. {@link ErrorList}
     * and the silent listeners are safe; a lambda that only reads is safe; one that accumulates
     * into a collection of its own must say how it is guarded.
     *
     * @param err  the error info
     */
    void log(ErrorInfo err);




    /**
     * Log an {@link Severity#INFO} diagnostic with no associated structure.
     *
     * <p>{@link Severity} already carries the levels every logger has - INFO, WARNING, ERROR,
     * FATAL - and {@code Slf4jErrorListener} already maps them onto exactly those log levels. These
     * aliases just let a call site name the level instead of passing it, which is how the severity
     * reads at the point it is decided.
     *
     * @param sCode    the error code
     * @param aoParam  the message parameters
     */
    default void info(String sCode, Object... aoParam) {
        log(Severity.INFO, sCode, Site.NONE, aoParam);
    }

    /**
     * Log a {@link Severity#WARNING} diagnostic with no associated structure.
     *
     * @param sCode    the error code
     * @param aoParam  the message parameters
     */
    default void warn(String sCode, Object... aoParam) {
        log(Severity.WARNING, sCode, Site.NONE, aoParam);
    }

    /**
     * Log a {@link Severity#ERROR} diagnostic with no associated structure.
     *
     * @param sCode    the error code
     * @param aoParam  the message parameters
     */
    default void error(String sCode, Object... aoParam) {
        log(Severity.ERROR, sCode, Site.NONE, aoParam);
    }

    /**
     * Log a {@link Severity#FATAL} diagnostic with no associated structure.
     *
     * @param sCode    the error code
     * @param aoParam  the message parameters
     */
    default void fatal(String sCode, Object... aoParam) {
        log(Severity.FATAL, sCode, Site.NONE, aoParam);
    }

    // NOTE: the obvious further convenience - log(Severity, String, Object...) for a diagnostic
    // with no structure at all - still cannot be added, for a reason that outlived the one that
    // used to be written here.
    //
    // The old reason was Launcher: it implements this interface and also declared exactly that
    // signature with the String meaning a "{}" template rather than an error code. That is fixed -
    // the method is Launcher.report now - so "log takes an error code" is finally true everywhere.
    //
    // The remaining reason is overload resolution, and it is not fixable by renaming. Adding
    // log(Severity, String, Object...) makes every existing call of the form
    //     log(sev, code, source, lStart, lEnd)
    // ambiguous, because the varargs overload absorbs (Source, long, long) as three parameters
    // just as well as the positional one takes them as a location. javac rejects those call sites
    // outright - Lexer:2650, ModuleInfo:1384 and AstNode:739 among them.
    //
    // So callers with no context pass a null XvmStructure to the overload above. Where the null is
    // untyped that needs no cast, because no other overload is applicable.

    /**
     * Where a diagnostic happened.
     *
     * <p>A type, rather than three positional parameters, and that is the whole point. Location used
     * to be encoded in the shape of the call - an {@link XvmStructure} in one overload, a
     * {@code (Source, long, long)} triple in another, nothing at all in the aliases - so every
     * convenience anyone tried to add collided with one of them. A varargs {@code Object...}
     * absorbs {@code (Source, long, long)} as three message parameters exactly as readily as the
     * positional overload takes them as a location, which is why {@code log(Severity, String,
     * Object...)} could never be added. A {@code Site} cannot be confused with a message parameter,
     * so it can be.</p>
     */
    sealed interface Site {
        /**
         * A diagnostic with no location. Distinct from "unknown": the caller HAS no location to
         * give, which used to be spelled as a null {@link XvmStructure} and a cast.
         */
        Site NONE = new None();

        /** Located at a structure. */
        record At(XvmStructure structure) implements Site {}

        /** Located at a span of source. */
        record In(Source source, long start, long end) implements Site {}

        /** @see #NONE */
        record None() implements Site {}
    }

    /**
     * @param structure  the structure the diagnostic concerns
     *
     * @return a {@link Site} at that structure
     */
    /**
     * Where a diagnostic was raised FROM, as opposed to what it is about.
     *
     * <p>{@link Site} says which source or structure the diagnostic concerns. This says which thread
     * - and, at run time, which fiber - was executing when it was raised. With parallel compiles
     * teeing into one host sink, "which of the eight running compiles produced this" is otherwise
     * unanswerable from the diagnostic alone.
     *
     * <p>The thread is captured automatically, because the executing thread is a FACT about the log
     * call and claims nothing about ownership. The fiber is not: it must be supplied by code that
     * holds a {@link org.xvm.runtime.Frame}, via {@link ErrorListener#onFiber}. Reading it ambiently
     * would mean asking "which fiber happens to be bound on this thread", which is the
     * hidden-ownership hazard this branch deleted along with {@code ServiceContext.getCurrentContext}
     * and {@code ConstantPool.getCurrentPool} - and {@code DisplayPurityTest} bans the accessor by
     * name so it cannot come back.
     *
     * <p><b>Never part of the deduplication key.</b> {@code genUID} must not include any of this: two
     * threads reporting the same diagnostic are reporting the same diagnostic, and keying on origin
     * would turn the fan-in case into duplicate output for every parallel compile.
     *
     * @param thread  the name of the thread that raised it
     * @param fiber   the id of the XTC fiber that raised it, or {@link #NO_FIBER} outside one
     */
    record Origin(@NotNull String thread, long fiber) {
        /**
         * The fiber id used when there is no fiber - all compile-time diagnostics, and any runtime
         * one raised outside fiber execution.
         */
        public static final long NO_FIBER = -1L;

        /**
         * @return the origin of a diagnostic raised on the calling thread, with no fiber
         */
        public static @NotNull Origin here() {
            return new Origin(Thread.currentThread().getName(), NO_FIBER);
        }

        /**
         * @param id  the fiber id
         *
         * @return this origin, attributed to the specified fiber
         */
        public @NotNull Origin withFiber(long id) {
            return new Origin(thread, id);
        }

        @Override
        public String toString() {
            return fiber == NO_FIBER ? thread : thread + "/fiber:" + fiber;
        }
    }

    static Site at(XvmStructure structure) {
        return new Site.At(structure);
    }

    /**
     * @param source  the source the diagnostic is in
     * @param start   the position where it begins
     * @param end     the position where it ends
     *
     * @return a {@link Site} at that span
     */
    static Site in(Source source, long start, long end) {
        return new Site.In(source, start, end);
    }

    /**
     * Log a diagnostic. The one shape: a severity, a code, where it happened, and its parameters.
     *
     * @param severity  the severity level
     * @param sCode     the error code
     * @param site      where the diagnostic happened
     * @param aoParam   the error message parameters
     */
    default void log(Severity severity, String sCode, Site site, Object... aoParam) {
        log(switch (site) {
            case Site.At(XvmStructure structure)          -> new ErrorInfo(severity, sCode, aoParam, structure);
            case Site.In(Source source, long start, long end) ->
                    new ErrorInfo(severity, sCode, aoParam, source, start, end);
            case Site.None ignored                        -> new ErrorInfo(severity, sCode, aoParam, (XvmStructure) null);
        });
    }


    /**
     * Branch this ErrorListener by creating a new one that will collect subsequent errors
     * in the same manner as this one until it is {@link #merge() merged} or discarded in the
     * (optional) context of the specified node.
     *
     * @param node  (optional) the context ast node
     *
     * @return the branched-out ErrorListener
     */
    default @NotNull ErrorListener branch(AstNode node) {
        return new ErrorList.BranchedErrorListener(this, 1, node);
    }

    /**
     * Obtain a listener for the remainder of a computation that has become incomplete.
     *
     * <p>Once a type or resolution step is known to have failed, the errors that follow are
     * consequences of that failure rather than independent problems, and surfacing them buries the
     * one the user needs to see. Cascade suppression is therefore correct and deliberate.
     *
     * <p>The way it was expressed before this method existed was to overwrite the caller's listener
     * with {@link #BLACKHOLE}, which has four problems: it is redundant with the completeness flag
     * that is invariably set on the same line; it mutates a parameter, so the decision is invisible
     * at the call site; it is irreversible, so a genuinely unrelated later error is dropped too;
     * and it destroys the errors rather than setting them aside, so nothing can afterwards ask what
     * was suppressed - which is exactly what one wants when diagnosing why the step failed.
     *
     * <p>This returns a branch instead. A branch collects, and only {@link #merge} promotes, so
     * declining to merge is already "record but do not surface" - the semantics wanted here, using
     * the mechanism the compiler already uses everywhere else. The caller keeps the reference and
     * may consult {@link #hasSeriousErrors} on it; dropping it discards the errors, exactly as
     * {@code BLACKHOLE} did, but by choice rather than by construction.
     *
     * @return a listener that collects subsequent errors without surfacing them
     */
    default @NotNull ErrorListener suppressCascade() {
        return branch(null);
    }

    /**
     * Merge all errors collected by this ErrorListener into the one it was branched out of.
     *
     * @return the ErrorListener this one was {@link #branch branched out} of
     */
    default @NotNull ErrorListener merge() {
        throw new UnsupportedOperationException("nothing to merge");
    }

    /**
     * Ask whether the process that is reporting diagnostics should stop.
     *
     * <p>This is the ONLY question about control flow. {@link #log} records; this decides. A
     * listener that keeps no state cannot answer it, and answers "no" - which is right: an observer
     * has no basis for stopping a compilation.
     *
     * @return true if the ErrorListener has decided to abort the process that reported the error
     */
    /**
     * Attribute everything logged through the returned listener to the specified fiber.
     *
     * <p>A decorator rather than a parameter, because {@link ErrorInfo} is built inside the severity
     * aliases and there is no per-call hook - and because forwarding {@code log} is all a decorator
     * has to do, which is the property that makes them safe to compose here.
     *
     * @param fiber  the fiber id to stamp on
     *
     * @return a listener that stamps the fiber and forwards to this one
     */
    default @NotNull ErrorListener onFiber(long fiber) {
        ErrorListener delegate = this;
        return new ErrorListener() {
            @Override
            public void log(ErrorInfo err) {
                delegate.log(err.attributedTo(err.getOrigin().withFiber(fiber)));
            }

            @Override
            public boolean isAbortDesired() {
                return delegate.isAbortDesired();
            }

            @Override
            public boolean isSilent() {
                return delegate.isSilent();
            }

            @Override
            public String toString() {
                return delegate + "/fiber:" + fiber;
            }
        };
    }

    default boolean isAbortDesired() {
        return false;
    }

    /**
     * @return true iff an error has been logged with at least the Severity of Error
     */
    default boolean hasSeriousErrors() {
        return false;
    }

    /**
     * @return true iff an error has been logged with the specified code
     */
    default boolean hasError(String sCode) {
        return false;
    }

    /**
     * Used for debugging only.
     *
     * @return true iff this listener discards what it is told; see {@link #PROBE} and
     *         {@link #BLACKHOLE}
     */
    default boolean isSilent() {
        return false;
    }


    // ----- inner class: silent listeners ---------------------------------------------------------

    /**
     * A listener that records nothing.
     *
     * <p>{@link #PROBE} and {@link #BLACKHOLE} share this implementation because they behave
     * identically and differ only in what they mean. The distinction is for the reader, so it is
     * carried by the constant's name and not by the type - nothing may branch on which one it has,
     * and {@code ==} against either is the mode-flag-in-disguise this listener exists to avoid.
     */
    class SilentErrorListener
            implements ErrorListener {
        private final String f_sName;

        SilentErrorListener(String sName) {
            f_sName = sName;
        }

        @Override
        public void log(ErrorInfo err) {
        }

        @Override
        public @NotNull ErrorListener merge() {
            return this;
        }

        @Override
        public boolean isSilent() {
            return true;
        }

        @Override
        public String toString() {
            return f_sName;
        }
    }


    // ----- inner class: Runtime ErrorListener ----------------------------------------------------

    /**
     * The listener an owner that nobody configured answers with: it prints, and that is all.
     *
     * <p>It used to throw {@link IllegalStateException} from inside {@code log} at ERROR and above.
     * That was the same defect as {@code log} returning "abort", in a sharper form - the listener
     * decided control flow, and it decided it differently from every other listener. A compile
     * logging an ERROR to an {@link ErrorList} records it and carries on; the same code logging the
     * same ERROR at run time blew up from wherever it happened to be. Worse, the throw pre-empted
     * whatever the detecting code intended to throw next, so the exception TYPE depended on who
     * owned the pool.
     *
     * <p>Now the runtime behaves like the compiler: the diagnostic is reported, and stopping is the
     * detecting code's decision, taken explicitly. Serious diagnostics go to {@code System.err} so
     * they are still hard to miss.
     */
    class RuntimeErrorListener
            implements ErrorListener {
        @Override
        public void log(ErrorInfo err) {
            String s = err.getSeverity() + ": " + err;
            if (err.getSeverity().ordinal() >= Severity.ERROR.ordinal()) {
                System.err.println(s);
            } else {
                System.out.println(s);
            }
        }

        @Override
        public String toString() {
            return "(Runtime error listener)";
        }
    }


    // ----- inner class: ErrorInfo ----------------------------------------------------------------

    /**
     * Represents the information logged for a single error.
     */
    class ErrorInfo {
        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; null is recorded as none
         * @param source      the source code
         * @param lPosStart   the starting position in the source code
         * @param lPosEnd     the ending position in the source code
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam,
                Source source, long lPosStart, long lPosEnd) {
            m_severity   = severity;
            m_sCode      = sCode;
            m_aoParam    = aoParam == null ? NO_PARAMS : aoParam;
            m_origin     = Origin.here();
            m_source     = source;
            m_lPosStart  = lPosStart;
            m_lPosEnd    = lPosEnd;
        }

        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; null is recorded as none
         * @param xs
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam == null ? NO_PARAMS : aoParam;
            m_origin   = Origin.here();
            m_xs       = xs;
            // TODO need to be able to ask the XVM structure for the source & location
        }

        /**
         * Copy this diagnostic with a different origin.
         *
         * <p>Copy rather than mutate: every other field is effectively final, and a diagnostic that
         * changed identity after being logged would break the deduplication the {@link ErrorList}
         * does on it. The origin is not part of that key, so the copy is equal to the original for
         * deduplication purposes and only differs in what it says about where it came from.
         *
         * @param origin  the origin to attribute it to
         *
         * @return a copy attributed to that origin, or this one if it already is
         */
        public @NotNull ErrorInfo attributedTo(@NotNull Origin origin) {
            if (origin.equals(m_origin)) {
                return this;
            }
            ErrorInfo that = m_source == null
                    ? new ErrorInfo(m_severity, m_sCode, m_aoParam, m_xs)
                    : new ErrorInfo(m_severity, m_sCode, m_aoParam, m_source, m_lPosStart, m_lPosEnd);
            that.m_xs     = m_xs;
            that.m_origin = origin;
            return that;
        }

        /**
         * @return where this diagnostic was raised from; never null
         */
        public @NotNull Origin getOrigin() {
            return m_origin;
        }

        /**
         * @return the Severity of the error
         */
        public Severity getSeverity() {
            return m_severity;
        }

        /**
         * @return the error code
         */
        public String getCode() {
            return m_sCode;
        }

        /**
         * @return the error message parameters
         */
        public Object[] getParams() {
            return copyOf(m_aoParam);
        }

        /**
         * Produce a localized message based on the error code and related parameters.
         *
         * @return a formatted message for display that includes the error code
         */
        public String getMessage() {
            return getCode() + ": " + getMessageText();
        }

        /**
         * Produce a localized message based on the error code and related parameters.
         *
         * @return a formatted message for display that doesn't include the error code
         */
        public String getMessageText() {
            return MessageFormat.format(RESOURCES.getString(getCode()), getParams());
        }

        /**
         * @return the source code
         */
        public Source getSource() {
            return m_source;
        }

        /**
         * @return the line number (zero based) at which the error occurred
         */
        public int getLine() {
            return Source.calculateLine(m_lPosStart);
        }

        /**
         * @return the offset (zero based) at which the error occurred
         */
        public int getOffset() {
            return Source.calculateOffset(m_lPosStart);
        }

        /**
         * @return the line number (zero based) at which the error concluded
         */
        public int getEndLine() {
            return Source.calculateLine(m_lPosEnd);
        }

        /**
         * @return the offset (zero based) at which the error concluded
         */
        public int getEndOffset() {
            return Source.calculateOffset(m_lPosEnd);
        }

        /**
         * @return the XvmStructure that this error is related to, or null
         */
        public XvmStructure getXvmStructure() {
            return m_xs;
        }

        /**
         * The key {@link ErrorList} deduplicates on: two diagnostics with the same UID are the same
         * diagnostic, and the second is dropped.
         *
         * <p>That makes precision here a correctness property, not a nicety - anything this merges
         * wrongly is a real diagnostic the user never sees. Two ways it used to merge wrongly:
         *
         * <ul>
         * <li>the end position was never part of the key. It appended {@code m_lPosStart} twice,
         *     where the second was meant to be {@code m_lPosEnd}, so two diagnostics that start at
         *     the same place and cover different spans collapsed into one;</li>
         * <li>parameters were compared by {@code Arrays.hashCode}, so two unrelated diagnostics
         *     whose parameter arrays happened to collide collapsed too. It now keys on the values,
         *     which is also the right semantic: parameters that render identically produce an
         *     identical message. {@code Arrays.toString} rather than {@code deepToString} because
         *     the array is one-dimensional and no call site passes an array as a parameter - and if
         *     one ever did, an identity hash in the key would only ever UNDER-merge, showing a
         *     duplicate rather than losing a diagnostic.</li>
         * </ul>
         *
         * <p>Building a string per call is affordable because this is a cold path: it runs once per
         * diagnostic actually logged, and a compile that succeeds logs none.
         *
         * @return an ID that allows redundant errors to be filtered out
         */
        public String genUID() {
            var sb = new StringBuilder();
            sb.append(m_severity.ordinal())
                    .append(':')
                    .append(m_sCode);

            if (m_aoParam.length > 0) {
                sb.append('#')
                  .append(Arrays.toString(m_aoParam));
            }

            if (!m_sCode.startsWith("VERIFY")) {
                if (m_source != null) {
                    sb.append(':')
                      .append(m_source.getFileName())
                      .append(':')
                      .append(m_lPosStart)
                      .append(':')
                      .append(m_lPosEnd);
                }
                if (m_xs != null) {
                    sb.append(':')
                      .append(m_xs.getDescription());
                }
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            // source code location
            if (m_source != null) {
                String sFile = m_source.getFileName();
                if (sFile != null) {
                    // output file:line as IntelliJ will then link to the line
                    if (INTELLIJ_IDEA) {
                        sb.append(sFile)
                            .append(" (")
                            .append(sFile.substring(sFile.lastIndexOf('/') + 1))
                            .append(':')
                            .append(getLine() + 1)
                            .append(") ");
                    } else {
                        sb.append(sFile)
                            .append(':').append(getLine() + 1)
                            .append(' ');
                    }
                }

                sb.append("[")
                  .append(getLine() + 1)
                  .append(':')
                  .append(getOffset() + 1);

                if (getEndLine() != getLine() || getEndOffset() != getOffset()) {
                    sb.append("..")
                      .append(getEndLine() + 1)
                      .append(':')
                      .append(getEndOffset() + 1);
                }

                sb.append("] ");
            }

            // XVM Structure id
            XvmStructure xs = getXvmStructure();
            while (xs != null) {
                Constant constId = xs.getIdentityConstant();
                if (constId == null) {
                    xs = xs.getContaining();
                } else {
                    sb.append("[")
                      .append(constId)
                      .append("] ");
                    break;
                }
            }

            // localized message
            sb.append(getMessage());

            // source code snippet
            if (m_source != null && m_lPosStart != m_lPosEnd) {
                String sSource = m_source.toString(m_lPosStart, m_lPosEnd);
                if (sSource.length() > 80) {
                    sSource = sSource.substring(0, 77) + "...";
                }

                sb.append(" (")
                  .append(quotedString(sSource))
                  .append(')');
            }

            return sb.toString();
        }

        private final Severity     m_severity;
        private final String       m_sCode;
        private static final Object[] NO_PARAMS = new Object[0];

        /**
         * The message parameters; never null, empty when there are none.
         *
         * <p>Nullable was a third state that meant the same as empty and had to be decoded at every
         * use - {@code genUID}, {@code toString} and {@code getParams} each carried a branch for it,
         * and {@code getParams} handed the null onward to {@code MessageFormat}, which accepts an
         * empty array and can throw on a null one. Coerced once, at construction.
         */
        private final Object[]     m_aoParam;

        /**
         * Where this diagnostic was raised from. Captured at construction, replaceable only by
         * {@link #attributedTo}, and deliberately absent from {@link #genUID} - see {@link Origin}.
         */
        private       Origin       m_origin;
        private       Source       m_source;
        private       long         m_lPosStart;
        private       long         m_lPosEnd;
        private       XvmStructure m_xs;
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Text of the error messages.
     */
    ResourceBundle RESOURCES = ResourceBundle.getBundle("errors");

    /**
     * The listener for a question: speculative work whose failure IS the answer, and whose failure
     * must therefore not be audible.
     *
     * <p>The compiler asks a great many questions - does this expression fit that type, does this
     * name resolve, which of these candidates is best, would this body validate. It asks them by
     * running the real machinery and looking at what comes back: a {@code TypeFit}, or {@code null},
     * or a clone that did or did not survive {@code validate}. The errors raised on the way to that
     * answer are not diagnostics about the user's program; they are the mechanics of the question,
     * and surfacing them would report a failure the compiler went on to recover from.
     *
     * <p>Behaviourally identical to {@link #BLACKHOLE}. It is a separate constant because the two
     * are separate intentions, and a reader at the call site could not otherwise tell a probe from
     * a discarded sink without reading the callee. {@code grep PROBE} is now the list of the
     * compiler's speculative paths.
     *
     * <p>Not for: work whose failure the user should hear about if every alternative also fails.
     * That is {@link #branch}, which keeps the errors so the caller can promote them with
     * {@link #merge} when it runs out of alternatives.
     */
    ErrorListener PROBE = new SilentErrorListener("(Probe)");

    /**
     * The listener for an absent sink: nobody is listening, so there is nowhere to put a diagnostic.
     *
     * <p>This is the null object that lets an {@code ErrorListener} parameter be non-null
     * everywhere. Before it, "no listener" was spelled {@code null} and every entry point
     * re-decided what that meant; now a caller that has no sink says so, once, at the call site.
     *
     * <p>Reaching for it to silence work that might fail is what {@link #PROBE} is for.
     */
    ErrorListener BLACKHOLE = new SilentErrorListener("(Blackhole)");

    /**
     * The listener an owner that nobody configured answers with; see {@link RuntimeErrorListener}.
     */
    ErrorListener RUNTIME = new RuntimeErrorListener();

    /**
     * Indicates that the compiler probably runs inside of IntelliJ IDEA.
     */
    boolean INTELLIJ_IDEA = ManagementFactory.getRuntimeMXBean().
                            getInputArguments().toString().contains("IntelliJ");
}
