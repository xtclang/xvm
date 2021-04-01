package org.xvm.asm;


import java.text.MessageFormat;

import java.util.Arrays;
import java.util.ResourceBundle;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.quotedString;


/**
 * A listener for errors being reported about source code, compilation, assembly, or verification of
 * XVM structures.
 */
public interface ErrorListener
    {
    // ----- API -----------------------------------------------------------------------------------

    /**
     * Handles the logging of an error that originates in Ecstasy source code.
     *
     * @param err  the error info
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt continue the process*
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
     *         false to attempt continue the process
     */
    default boolean log(Severity severity, String sCode, Object[] aoParam,
            Source source, long lPosStart, long lPosEnd)
        {
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
    default boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
        {
        return log(new ErrorInfo(severity, sCode, aoParam, xs));
        }

    /**
     * Branch this ErrorListener by creating a new one that will collect subsequent errors
     * in the same manner as this one until it is {@link #merge() merged} or discarded.
     *
     * @return the branched-out ErrorListener
     */
    default ErrorListener branch()
        {
        return new ErrorList.BranchedErrorListener(this, 1);
        }

    /**
     * Merge all errors collected by this ErrorListener into the one it was branched out of.
     *
     * @return the ErrorListener this one was {@link #branch() branched out} of
     */
    default ErrorListener merge()
        {
        throw new UnsupportedOperationException("nothing to merge");
        }

    /**
     * @return true if the ErrorListener has decided to abort the process that reported the error
     */
    default boolean isAbortDesired()
        {
        return false;
        }

    /**
     * @return true iff an error has been logged with at least the Severity of Error
     */
    default boolean hasSeriousErrors()
        {
        return false;
        }

    /**
     * @return true iff an error has been logged with the specified code
     */
    default boolean hasError(String sCode)
        {
        return false;
        }

    /**
     * Used for debugging only.
     *
     * @return true iff this listener sits on top of the BlackHoleListener
     */
    default boolean isSilent()
        {
        return false;
        }


    // ----- inner class: BlackholeErrorListener ---------------------------------------------------

    /**
     * A simple implementation of the ErrorListener that converts reported errors to ErrorInfo
     * objects and routes them to a single sink method.
     */
    class BlackholeErrorListener
            implements ErrorListener
        {
        @Override
        public boolean log(ErrorInfo err)
            {
            return false;
            }

        @Override
        public boolean isSilent()
            {
            return true;
            }

        @Override
        public String toString()
            {
            return "(Blackhole)";
            }
        }


    // ----- inner class: Runtime ErrorListener ----------------------------------------------------

    /**
     * A simple implementation of the ErrorListener that can be used at runtime. Errors will throw,
     * and non-errors will go to standard out.
     */
    class RuntimeErrorListener
            implements ErrorListener
        {
        @Override
        public boolean log(ErrorInfo err)
            {
            String s = err.toString();
            if (err.getSeverity().ordinal() >= Severity.ERROR.ordinal())
                {
                throw new IllegalStateException(s);
                }
            else
                {
                System.out.println(s);
                return false;
                }
            }

        @Override
        public boolean isSilent()
            {
            return false;
            }

        @Override
        public String toString()
            {
            return "(Runtime error listener)";
            }
        };


    // ----- inner class: ErrorInfo ----------------------------------------------------------------

    /**
     * Represents the information logged for a single error.
     */
    class ErrorInfo
        {
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
                Source source, long lPosStart, long lPosEnd)
            {
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
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
            {
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam;
            m_xs       = xs;
            // TODO need to be able to ask the XVM structure for the source & location
            }

        /**
         * @return the Severity of the error
         */
        public Severity getSeverity()
            {
            return m_severity;
            }

        /**
         * @return the error code
         */
        public String getCode()
            {
            return m_sCode;
            }

        /**
         * @return the error message parameters
         */
        public Object[] getParams()
            {
            return m_aoParam;
            }

        /**
         * Produce a localized message based on the error code and related
         * parameters.
         *
         * @return a formatted message for display
         */
        public String getMessage()
            {
            return getCode() + ": " + MessageFormat.format(RESOURCES.getString(getCode()), getParams());
            }

        /**
         * @return the source code
         */
        public Source getSource()
            {
            return m_source;
            }

        /**
         * @return the starting position in the source (opaque)
         */
        public long getPos()
            {
            return m_lPosStart;
            }

        /**
         * @return the line number (zero based) at which the error occurred
         */
        public int getLine()
            {
            return Source.calculateLine(m_lPosStart);
            }

        /**
         * @return the offset (zero based) at which the error occurred
         */
        public int getOffset()
            {
            return Source.calculateOffset(m_lPosStart);
            }

        /**
         * @return the ending position in the source (opaque)
         */
        public long getEndPos()
            {
            return m_lPosEnd;
            }

        /**
         * @return the line number (zero based) at which the error concluded
         */
        public int getEndLine()
            {
            return Source.calculateLine(m_lPosEnd);
            }

        /**
         * @return the offset (zero based) at which the error concluded
         */
        public int getEndOffset()
            {
            return Source.calculateOffset(m_lPosEnd);
            }

        /**
         * @return the XvmStructure that this error is related to, or null
         */
        public XvmStructure getXvmStructure()
            {
            return m_xs;
            }

        /**
         * @return an ID that allows redundant errors to be filtered out
         */
        public String genUID()
            {
            StringBuilder sb = new StringBuilder();
            sb.append(m_severity.ordinal())
                    .append(':')
                    .append(m_sCode);

            if (m_xs != null)
                {
                sb.append(':')
                  .append(m_xs.getDescription());
                }

            if (m_aoParam != null)
                {
                sb.append('#')
                  .append(Arrays.hashCode(m_aoParam));
                }

            if (m_source != null)
                {
                sb.append(':')
                  .append(m_source.getFileName())
                  .append(':')
                  .append(m_lPosStart)
                  .append(':')
                  .append(m_lPosStart);
                }

            return sb.toString();
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            // source code location
            if (m_source != null)
                {
                String sFile = m_source.getFileName();
                if (sFile != null)
                    {
                    sb.append(sFile)
                      .append(' ');
                    }

                sb.append("[")
                  .append(getLine() + 1)
                  .append(':')
                  .append(getOffset() + 1);

                if (getEndLine() != getLine() || getEndOffset() != getOffset())
                    {
                    sb.append("..")
                      .append(getEndLine() + 1)
                      .append(':')
                      .append(getEndOffset() + 1);
                    }

                sb.append("] ");
                }

            // XVM Structure id
            XvmStructure xs = getXvmStructure();
            while (xs != null)
                {
                Constant constId = xs.getIdentityConstant();
                if (constId == null)
                    {
                    xs = xs.getContaining();
                    }
                else
                    {
                    sb.append("[")
                      .append(constId)
                      .append("] ");
                    break;
                    }
                }

            // localized message
            sb.append(getMessage());

            // source code snippet
            if (m_source != null && m_lPosStart != m_lPosEnd)
                {
                String sSource = m_source.toString(m_lPosStart, m_lPosEnd);
                if (sSource.length() > 80)
                    {
                    sSource = sSource.substring(0, 77) + "...";
                    }

                sb.append(" (")
                  .append(quotedString(sSource))
                  .append(')');
                }

            return sb.toString();
            }

        private Severity     m_severity;
        private String       m_sCode;
        private Object[]     m_aoParam;
        private Source       m_source;
        private long         m_lPosStart;
        private long         m_lPosEnd;
        private XvmStructure m_xs;
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
    }
