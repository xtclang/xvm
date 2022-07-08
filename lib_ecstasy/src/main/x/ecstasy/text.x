package text
    {
    /**
     * A Log is simply an `Appender<String>`.
     */
    typedef Appender<String> as Log;

    /**
     * Simple Log implementation.
     */
    class SimpleLog
            delegates Log(errors)
        {
        String[] errors = new String[];

        @Override
        String toString()
            {
            StringBuffer buf = new StringBuffer(
                errors.estimateStringLength(sep="\n", pre="", post=""));
            return errors.appendTo(buf, sep="\n", pre="", post="").toString();
            }
        }
    }