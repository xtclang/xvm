/**
 * This package provides the reflection implementation for the Ecstasy language.
 */
package reflect {
    /**
     * An TypeRequired exception is raised when a type is required, such as for a formal type
     * parameter.
     */
    const TypeRequired(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An InvalidType exception is raised to indicate that the production of a requested type would
     * violate the rules of the type system.
     */
    const InvalidType(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An InvalidClass exception is raised to indicate that the production of a requested class (a
     * combination of a composition and a set of formal type parameter values) would violate the
     * rules of the type system.
     */
    const InvalidClass(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A UnboundFormalParameter exception is raised when an attempt is made to bind a function
     * parameter or invoke a function with parameters without having successfully bound all of the
     * formal type parameters.
     */
    const UnboundFormalParameter(String? text = Null, Exception? cause = Null)
            extends IllegalState(text, cause);

    /**
     * `Access` is an enumeration of the modifiers that can be used on top of a type to specify
     * a different view of the underlying type.
     */
    enum Access(String keyword) {
        Public   ("public"),
        Protected("protected"),
        Private  ("private"),
        Struct   ("struct")
    }

    /**
     * Represents the information about an annotation, including the template representing the
     * annotation, and the argument values for the annotation.
     */
    const AnnotationTemplate(ClassTemplate template, Argument[] arguments);

    /**
     * SourceCodeInfo provides information about the name of the file that contains source code,
     * and the 0-based line number within that file that the relevant source code begins.
     */
    const SourceCodeInfo(String sourceFile, Int lineNumber);
}