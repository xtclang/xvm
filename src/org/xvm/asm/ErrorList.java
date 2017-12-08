package org.xvm.asm;


import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.xvm.util.Severity;


/**
 * Represents a list of errors collected from a compilation process, with an
 * option to abort the compilation should a maximum number of errors be
 * exceeded.
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
    public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
        {
        // remember the highest severity encountered
        if (severity.ordinal() > m_severity.ordinal())
            {
            m_severity = severity;
            }

        // accumulate all the errors in a list
        m_list.add(new ErrorInfo(severity, sCode, aoParam, xs));

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
         * @param xs
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
            {
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam;
            m_xs       = xs;
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
            String sMessage = getMessage();

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
                    return "[" + constId + "] " + sMessage;
                    }
                }

            return sMessage;
            }

        private Severity     m_severity;
        private String       m_sCode;
        private Object[]     m_aoParam;
        private XvmStructure m_xs;
        }


    // ----- data members ------------------------------------------------------

    /**
     * Text of the error messages.
     */
    public static final ResourceBundle RESOURCES = ResourceBundle.getBundle("assembler");

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
