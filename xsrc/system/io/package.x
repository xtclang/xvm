package io
    {
    /**
     * A IllegalUTF is raised when an illegal character is encountered in a byte stream.
     */
    const IllegalUTF(String? text = null, Exception? cause = null)
            extends Exception(text, cause)
        {
        }
    }