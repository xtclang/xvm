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
 *       the no-argument overload consult the pool. 66 call sites still use that overload; see E35
 *       step D in the enhancement list. The pool default is a sound FALLBACK, but it should never
 *       win over an explicit caller.
 *
 *       What IS newly true is that wrapping one owner captures everything downstream OF THAT OWNER.
 *       That was not true at all while half the paths defaulted to BLACKHOLE or consulted a mutable
 *       field on FileStructure - there was no single place to wrap.
 */
public interface ErrorListener {
    // ----- API -----------------------------------------------------------------------------------

    /**
     * Handles the logging of an error that originates in Ecstasy source code.
     *
     * @param err  the error info
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt to continue the process
     */
    boolean log(ErrorInfo err);

    /**
     * Handles the logging of an error that originates in Ecstasy source code.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param source      the source code (optional)
     * @param lPosStart   the position in the source where the error was detected
     * @param lPosEnd     the position in the source at which the error concluded
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt to continue the process
     */
    default boolean log(Severity severity, String sCode, Object[] aoParam,
            Source source, long lPosStart, long lPosEnd) {
        return log(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
    }

    /**
     * Handles the logging of an error that originates in an Ecstasy XVM structure.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING,
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param xs          the XvmStructure that the error is related to; may
     *                    be null
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt continue the process
     */
    default boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
        return log(new ErrorInfo(severity, sCode, aoParam, xs));
    }

    /**
     * Log an error, taking the message parameters as varargs.
     *
     * <p>Identical to {@link #log(Severity, String, Object[], XvmStructure)} but with the
     * parameters last, so callers write the values directly instead of building an
     * {@code Object[]} at the call site. Added rather than replacing, because a varargs parameter
     * must come last and the existing signature cannot be reordered without breaking every caller.
     *
     * @param severity  the severity level
     * @param sCode     the error code
     * @param xs        the XvmStructure the error relates to
     * @param aoParam   the error message parameters
     *
     * @return true if the process should be aborted
     */
    default boolean log(Severity severity, String sCode, XvmStructure xs, Object... aoParam) {
        return log(new ErrorInfo(severity, sCode, aoParam, xs));
    }

    /**
     * Log an error against a source position, taking the message parameters as varargs.
     *
     * @param severity   the severity level
     * @param sCode      the error code
     * @param source     the source that the error is in
     * @param lPosStart  the position in the source where the error begins
     * @param lPosEnd    the position in the source where the error ends
     * @param aoParam    the error message parameters
     *
     * @return true if the process should be aborted
     */
    default boolean log(Severity severity, String sCode, Source source, long lPosStart, long lPosEnd,
            Object... aoParam) {
        return log(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
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
    default ErrorListener branch(AstNode node) {
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
    default ErrorListener suppressCascade() {
        return branch(null);
    }

    /**
     * Merge all errors collected by this ErrorListener into the one it was branched out of.
     *
     * @return the ErrorListener this one was {@link #branch branched out} of
     */
    default ErrorListener merge() {
        throw new UnsupportedOperationException("nothing to merge");
    }

    /**
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
        public boolean log(ErrorInfo err) {
            return false;
        }

        @Override
        public ErrorListener merge() {
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
     * A simple implementation of the ErrorListener that can be used at runtime. Errors will throw,
     * and non-errors will go to standard out.
     */
    class RuntimeErrorListener
            implements ErrorListener {
        @Override
        public boolean log(ErrorInfo err) {
            String s = err.toString();
            if (err.getSeverity().ordinal() >= Severity.ERROR.ordinal()) {
                throw new IllegalStateException(s);
            } else {
                System.out.println(err.getSeverity() + ": " + s);
                return false;
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
