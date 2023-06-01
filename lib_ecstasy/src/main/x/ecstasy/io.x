package io {
    /**
     * Indicates that an exception related to input/output has occurred.
     */
    const IOException(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A IllegalUTF is raised when an illegal character is encountered in a byte stream.
     */
    const IllegalUTF(String? text = Null, Exception? cause = Null)
            extends IOException(text, cause);

    /**
     * Indicates that an end of file or stream has been reached unexpectedly.
     */
    const EndOfFile(String? text = Null, Exception? cause = Null)
            extends IOException(text, cause);

    /**
     * Indicates that an operation failed because a file or stream closed before the operation
     * completed.
     */
    const IOClosed(String? text = Null, Exception? cause = Null)
            extends IOException(text, cause);

    /**
     * A DataInputStream combines an InputStream with a DataInput.
     */
    interface DataInputStream
            extends InputStream
            extends DataInput;

    /**
     * A DataOutputStream combines an OutputStream with a DataOutput.
     */
    interface DataOutputStream
            extends OutputStream
            extends DataOutput;

    /**
     * A Writer is simply an `Appender<Char>`.
     */
    typedef Appender<Char> as Writer;
}