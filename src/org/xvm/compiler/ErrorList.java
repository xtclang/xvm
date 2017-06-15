package org.xvm.compiler;


import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

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
            Source source, long lPosStart, long lPosEnd)
        {
        // remember the highest severity encountered
        if (severity.ordinal() > m_severity.ordinal())
            {
            m_severity = severity;
            }

        // accumulate all the errors in a list
        m_list.add(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));

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

    /**
     * Clear the list of errors, resetting the error collection state.
     */
    public void clear()
        {
        m_list.clear();
        m_cErrors  = 0;
        m_severity = Severity.NONE;
        }


    // ----- inner class: ErrorInfo --------------------------------------------

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
         * TODO
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
            return MessageFormat.format(RESOURCES.getString(getCode()), getParams());
            /*
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

            return sb.toString();
            */
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

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            String sFile = m_source == null ? null : m_source.getFileName();
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

            sb.append("] ")
              .append(getMessage());

            if (m_source != null && m_lPosStart != m_lPosEnd)
                {
                sb.append(" (\"")
                  .append(m_source.toString(m_lPosStart, m_lPosEnd))
                  .append("\")");
                }

            return sb.toString();
            }

        private Severity m_severity;
        private String   m_sCode;
        private Object[] m_aoParam;
        private Source   m_source;
        private long     m_lPosStart;
        private long     m_lPosEnd;
        }


    // ----- data members ------------------------------------------------------

    public static final ResourceBundle RESOURCES = ResourceBundle.getBundle("compiler");

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
