package reflect
    {
    /**
     * An TypeRequired exception is raised when a type is required, such as for a formal type
     * parameter.
     */
    const TypeRequired(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An InvalidClass exception is raised when TODO
     */
    const InvalidClass(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * TODO
     */
    const Argument<Referent extends immutable Const>(Referent value, String? name = Null);

    /**
     * Represents the information about an annotation, including the template representing the
     * annotation, and the argument values for the annotation.
     */
    const Annotation(ClassTemplate template, Argument[] arguments);
    }