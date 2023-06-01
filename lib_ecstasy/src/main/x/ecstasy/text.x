package text {
    /**
     * A Log is simply an `Appender<String>`.
     */
    typedef Appender<String> as Log;

    /**
     * Simple Log implementation.
     */
    class SimpleLog
            delegates Log(messages) {

        protected String[] messages = new String[];

        /**
         * True if the log is empty.
         */
        Boolean empty.get() {
            return messages.empty;
        }

        /**
         * Clean up all messages.
         */
        void reset() {
            messages.clear();
        }

        @Override
        String toString() {
            StringBuffer buf = new StringBuffer(
                messages.estimateStringLength(sep="\n", pre="", post=""));
            return messages.appendTo(buf, sep="\n", pre="", post="").toString();
        }
    }
}