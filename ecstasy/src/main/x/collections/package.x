package collections
    {
    /**
     * Any object that is already immutable, or explicitly provides the ability to be made
     * immutable.
     */
    typedef (immutable Object | Freezable) ImmutableAble;

    /**
     * An ConstantRequired exception is raised when an attempt is made to change mutability to
     * Constant, and some reference that must be `immutable Const` cannot be made so or converted
     * to be so.
     */
    const ConstantRequired(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An SizeLimited exception is raised when an attempt is made to alter a data structure in a
     * manner that would exceed its maximum size.
     */
    const SizeLimited(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);
    }
