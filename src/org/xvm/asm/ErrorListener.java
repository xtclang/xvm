package org.xvm.asm;


import org.xvm.compiler.Source;

import org.xvm.util.Severity;


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
    public boolean log(Severity severity, String sCode, Object[] aoParam,
            Source source, long lPosStart, long lPosEnd);

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
    public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs);

    /**
     * @return true if the ErrorListener has decided to abort the process that reported the error
     */
    public default boolean isAbortDesired()
        {
        return false;
        }


    // ----- inner class: BlackholeErrorListener ---------------------------------------------------

    /**
     * A simple implementation of the ErrorListener that converts reported errors to ErrorInfo
     * objects and routes them to a single sink method.
     */
    public class BlackholeErrorListener
            implements ErrorListener
        {
        @Override
        public boolean log(Severity severity, String sCode, Object[] aoParam,
                Source source, long lPosStart, long lPosEnd)
            {
            return log(new ErrorList.ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
            }

        @Override
        public boolean log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs)
            {
            return log(new ErrorList.ErrorInfo(severity, sCode, aoParam, xs));
            }

        /**
         * This is the sink method for errors.
         *
         * @param err  the error being logged
         *
         * @return true if the ErrorListener has decided to abort the process that reported the
         *         error
         */
        protected boolean log(ErrorList.ErrorInfo err)
            {
            return isAbortDesired();
            }
        }


    // ----- inner class: Runtime ErrorListener ----------------------------------------------------

    /**
     * A simple implementation of the ErrorListener that can be used at runtime. Errors will throw,
     * and non-errors will go to standard out.
     */
    public class RuntimeErrorListener
            extends BlackholeErrorListener
        {
        @Override
        protected boolean log(ErrorList.ErrorInfo err)
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
        };


    // ----- inner class: SilentErrorListener ------------------------------------------------------

    /**
     * A simple implementation of the ErrorListener that can be used to capture errors. The first
     * error gets saved.
     */
    public class SilentErrorListener
            extends BlackholeErrorListener
        {
        @Override
        public boolean isAbortDesired()
            {
            return m_err != null;
            }

        public ErrorList.ErrorInfo getFirstError()
            {
            return m_err;
            }

        @Override
        protected boolean log(ErrorList.ErrorInfo err)
            {
            if (m_err == null && err.getSeverity().ordinal() >= Severity.ERROR.ordinal())
                {
                m_err = err;
                }

            return super.log(err);
            }

        private ErrorList.ErrorInfo m_err;
        }


    // ----- constants -----------------------------------------------------------------------------

    public static final ErrorListener BLACKHOLE = new BlackholeErrorListener();
    public static final ErrorListener RUNTIME   = new RuntimeErrorListener();
    }
