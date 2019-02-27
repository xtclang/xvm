package io
    {
    /**
     * A UTFDataFormatException is raised when an illegal character is encountered in a byte stream.
     */
    const UTFDataFormatException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        }
    }