package io
    {
    /**
     * Indicates that an exception related to input/output has occurred.
     */
    const IOException(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * A IllegalUTF is raised when an illegal character is encountered in a byte stream.
     */
    const IllegalUTF(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * Indicates that an end of file or stream has been reached unexpectedly.
     */
    const EndOfFile(String? text = null, Exception? cause = null)
            extends IOException(text, cause);

    interface DataInputStream
            extends InputStream
            extends DataInput;

    interface DataOutputStream
            extends OutputStream
            extends DataOutput;
    }