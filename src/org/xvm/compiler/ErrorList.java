package org.xvm.compiler;


import java.util.ArrayList;
import java.util.List;

import org.xvm.util.Severity;


/**
 * Represents a list of errors collected from a compilation process, with an
 * option to abort the compilation should a maximum number of errors be
 * exceeded.
 *
 * @author cp 2015.11.13
 */
public class ErrorList
        implements ErrorListener
    {
    // ----- constructors ------------------------------------------------------

    public ErrorList(int cMaxErrors)
        {
        m_cMaxErrors = cMaxErrors;
        }


    // ----- ErrorListener methods ---------------------------------------------

    @Override
    public boolean log(Severity severity, String sCode, Object[] aoParam,
            int iLineStart, int ofStart, int iLineEnd, int ofEnd)
        {
        // remember the highest severity encountered
        if (severity.ordinal() > m_severity.ordinal())
            {
            m_severity = severity;
            }

        // accumulate all the errors in a list
        m_list.add(new ErrorInfo(severity, sCode, aoParam, iLineStart, ofStart, iLineEnd, ofEnd));

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


    // ----- accessors ---------------------------------------------------------

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
     * @return maximum number of serious errors enountered before attempting to
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


    // ----- inner class: ErrorInfo --------------------------------------------

    /**
     * Represents the information logged for a single error.
     */
    public class ErrorInfo
        {
        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         * @param iLineStart  the line number where the error was detected
         * @param ofStart     the offset in the line where the error was
         *                    detected
         * @param iLineEnd    the line number at which the error concluded
         * @param ofEnd       the offset in the line where the error concluded
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam,
                int iLineStart, int ofStart, int iLineEnd, int ofEnd)
            {
            m_severity = severity;
            m_sCode = sCode;
            m_aoParam = aoParam;
            m_iLineStart = iLineStart;
            m_ofStart = ofStart;
            m_iLineEnd = iLineEnd;
            m_ofEnd = ofEnd;
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
            // TODO - need to look up and format message correctly; this is just temporary
            StringBuilder sb = new StringBuilder(getCode());
            Object[] aoParam = getParams();
            if (aoParam != null)
                {
                sb.append(": ");
                for (int i = 0, c = aoParam.length; i < c; ++i)
                    {
                    if (i > 0)
                        {
                        sb.append(", ");
                        }

                    sb.append('[')
                      .append(i)
                      .append("]=");

                    Object o = aoParam[i];
                    if (o instanceof String)
                        {
                        sb.append('\"')
                          .append(o)
                          .append('\"');

                        }
                    else
                        {
                        sb.append(o);
                        }
                    }
                }

            return null;
            }

        /**
         * @return the line number (zero based) at which the error occurrred
         */
        public int getLine()
            {
            return m_iLineStart;
            }

        /**
         * @return the offset (zero based) at which the error occurred
         */
        public int getOffset()
            {
            return m_ofStart;
            }

        /**
         * @return the line number (zero based) at which the error concluded
         */
        public int getEndLine()
            {
            return m_iLineEnd;
            }

        /**
         * @return the offset (zero based) at which the error concluded
         */
        public int getEndOffset()
            {
            return m_ofEnd;
            }

        @Override
        public String toString()
            {
            return "[" + getLine() + ":" + getOffset() + ".."
                    + getEndLine() + ":" + getEndOffset() + "] "  + getMessage();
            }

        private Severity m_severity;
        private String   m_sCode;
        private Object[] m_aoParam;
        private int      m_iLineStart;
        private int      m_ofStart;
        private int      m_iLineEnd;
        private int      m_ofEnd;
        }


    // ----- data members ------------------------------------------------------

    /**
     * Maximum number of serious errors to tolerate before abandoning the
     * process.
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
