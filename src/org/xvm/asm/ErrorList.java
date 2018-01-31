package org.xvm.asm;


import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.quotedString;


/**
 * Represents a list of errors collected from a process such as compilation, assembly, or the
 * verifier, with an option to abort the process should a maximum number of errors be exceeded.
 */
public class ErrorList
        implements ErrorListener
    {
    // ----- constructors --------------------------------------------------------------------------

    public ErrorList(int cMaxErrors)
        {
        m_cMaxErrors = cMaxErrors;
        }


    // ----- ErrorListener methods -----------------------------------------------------------------

    @Override
    public boolean log(Severity severity, String sCode, Object[] aoParam,
            Source source, long lPosStart, long lPosEnd)
        {
        return log(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
        }

    @Override
    public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
        {
        return log(new ErrorInfo(severity, sCode, aoParam, xs));
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the severity of the ErrorList, which is the severity of the worst
     *         error encountered
     */
    public Severity getSeverity()
        {
        return m_severity;
        }

    /**
     * @return the count of serious errors encountered
     */
    public int getSeriousErrorCount()
        {
        return m_cErrors;
        }

    /**
     * @return maximum number of serious errors encountered before attempting to
     *         abort the process reporting the errors
     */
    public int getSeriousErrorMax()
        {
        return m_cMaxErrors;
        }

    /**
     * @return the list of ErrorInfo objects
     */
    public List<ErrorInfo> getErrors()
        {
        return m_list;
        }

    /**
     * Clear the list of errors, resetting the error collection state.
     */
    public void clear()
        {
        m_list.clear();
        m_cErrors  = 0;
        m_severity = Severity.NONE;
        }

    @Override
    public String toString()
        {
        if (m_cErrors == 0)
            {
            return "Empty";
            }

        return "Count=" + m_cErrors
                + ", Severity=" +  m_severity.name()
                + ", Last=" + m_list.get(m_list.size()-1);
        }


    // ----- internal ------------------------------------------------------------------------------

    protected boolean log(ErrorInfo err)
        {
        // remember the highest severity encountered
        Severity severity = err.getSeverity();
        if (severity.ordinal() > m_severity.ordinal())
            {
            m_severity = severity;
            }

        // accumulate all the errors in a list
        m_list.add(err);

        // keep track of the number of serious errors; quit the process once
        // that number grows too large
        if (severity.ordinal() >= Severity.ERROR.ordinal())
            {
            if (++m_cErrors >= m_cMaxErrors && m_cMaxErrors > 0)
                {
                return true;
                }
            }

        return false;
        }


    // ----- inner class: ErrorInfo ----------------------------------------------------------------

    /**
     * Represents the information logged for a single error.
     */
    public static class ErrorInfo
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
         * @return the starting position in the source (opaque)
         */
        public long getPos()
            {
            return m_lPosStart;
            }

        /**
         * @return the line number (zero based) at which the error occurrred
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


    // ----- data members --------------------------------------------------------------------------

    /**
     * Text of the error messages.
     */
    public static final ResourceBundle RESOURCES = ResourceBundle.getBundle("errors");

    /**
     * Maximum number of serious errors to tolerate before abandoning the process.
     */
    private int m_cMaxErrors;

    /**
     * The number of serious errors encountered.
     */
    private int m_cErrors;

    /**
     * The worst severity encountered.
     */
    private Severity m_severity = Severity.NONE;

    /**
     * The accumulated list of errors.
     */
    private ArrayList<ErrorInfo> m_list = new ArrayList<>();
    }
