/**
 * This package provides the reflection implementation for the Ecstasy language.
 */
package reflect
    {
    /**
     * An TypeRequired exception is raised when a type is required, such as for a formal type
     * parameter.
     */
    const TypeRequired(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An InvalidType exception is raised to indicate that the production of a requested type would
     * violate the rules of the type system.
     */
    const InvalidType(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An InvalidClass exception is raised to indicate that the production of a requested class (a
     * combination of a composition and a set of formal type parameter values) would violate the
     * rules of the type system.
     */
    const InvalidClass(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An Argument represents a value for a parameter. The argument optionally supports a name that
     * can be used to specify the name of the parameter for which the argument's value is intended.
     */
    const Argument<Referent extends immutable Const>(Referent value, String? name = Null);

    /**
     * Represents the information about an annotation, including the template representing the
     * annotation, and the argument values for the annotation.
     */
    const Annotation(ClassTemplate template, Argument[] arguments);
    }