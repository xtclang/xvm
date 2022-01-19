package collections
    {
    /**
     * An SizeLimited exception is raised when an attempt is made to alter a data structure in a
     * manner that would exceed its maximum size.
     */
    const SizeLimited(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);
    }
