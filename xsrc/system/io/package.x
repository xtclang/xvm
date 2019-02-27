package io
    {
    /**
     * A UTFDataFormatException is raised when an illegal character is encountered in a byte stream.
     */
    const UTFDataFormatException(String? text = null, Exception? cause = null)
            extends Exception(text, cause)
        {
        }
    }