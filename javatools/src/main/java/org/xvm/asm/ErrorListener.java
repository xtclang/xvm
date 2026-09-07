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
 *       so resolving a LIBRARY type runs `libType.ensureTypeInfo()` -> the library's pool ->
 *       ErrorListener.RUNTIME, and the caller never hears it.
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
     * @return true iff this listener sits on top of the BlackHoleListener
     */
    default boolean isSilent() {
        return false;
    }


    // ----- inner class: BlackholeErrorListener ---------------------------------------------------

    /**
     * A simple implementation of the ErrorListener that converts reported errors to ErrorInfo
     * objects and routes them to a single sink method.
     */
    class BlackholeErrorListener
            implements ErrorListener {
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
            return "(Blackhole)";
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
         * @param aoParam     the parameters for the error message; may be null
         * @param source      the source code
         * @param lPosStart   the starting position in the source code
         * @param lPosEnd     the ending position in the source code
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam,
                Source source, long lPosStart, long lPosEnd) {
            m_severity   = severity;
            m_sCode      = sCode;
            m_aoParam    = aoParam;
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
         * @param aoParam     the parameters for the error message; may be null
         * @param xs
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam;
            m_xs       = xs;
            // TODO need to be able to ask the XVM structure for the source & location
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
            return m_aoParam == null ? null : copyOf(m_aoParam);
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
         * @return an ID that allows redundant errors to be filtered out
         */
        public String genUID() {
            StringBuilder sb = new StringBuilder();
            sb.append(m_severity.ordinal())
                    .append(':')
                    .append(m_sCode);

            if (m_aoParam != null) {
                sb.append('#')
                  .append(Arrays.hashCode(m_aoParam));
            }

            if (!m_sCode.startsWith("VERIFY")) {
                if (m_source != null) {
                    sb.append(':')
                      .append(m_source.getFileName())
                      .append(':')
                      .append(m_lPosStart)
                      .append(':')
                      .append(m_lPosStart);
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
            StringBuilder sb = new StringBuilder();

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
        private final Object[]     m_aoParam;
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
     * Stateless ErrorListeners.
     */
    ErrorListener BLACKHOLE = new BlackholeErrorListener();
    ErrorListener RUNTIME   = new RuntimeErrorListener();

    /**
     * Indicates that the compiler probably runs inside of IntelliJ IDEA.
     */
    boolean INTELLIJ_IDEA = ManagementFactory.getRuntimeMXBean().
                            getInputArguments().toString().contains("IntelliJ");
}
