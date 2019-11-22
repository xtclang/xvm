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
     * A UnboundFormalParameter exception is raised when an attempt is made to bind a function
     * parameter or invoke a function with parameters without having successfully bound all of the
     * formal type parameters.
     */
    const UnboundFormalParameter(String? text = null, Exception? cause = null)
            extends IllegalState(text, cause);

    /**
     * `Access` is an enumeration of the modifiers that can be used on top of a type to specify
     * a different view of the underlying type.
     */
    enum Access(String keyword)
        {
        Public   ("public"),
        Protected("protected"),
        Private  ("private"),
        Struct   ("struct")
        }

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

    /**
     * Represents a function parameter, including type parameters.
     */
    interface Parameter<ParamType>
            extends immutable Const
        {
        /**
         * The ordinal index of the parameter.
         */
        @RO Int ordinal;

        /**
         * Determine the parameter name.
         *
         * @return True iff the parameter has a name
         * @return (conditional) the parameter name
         */
        conditional String hasName();

        /**
         * Indicates whether the parameter is a formal type parameter.
         */
        @RO Boolean formal;

        /**
         * Determine the default argument value for the parameter, if any.
         *
         * @return True iff the parameter has a default argument value
         * @return (conditional) the default argument value
         */
        conditional ParamType defaultValue();

        @Override
        Int estimateStringLength()
            {
            Int len = ParamType.estimateStringLength();
            if (String name := hasName())
                {
                len += 1 + name.size;
                }
            return len;
            }

        @Override
        void appendTo(Appender<Char> appender)
            {
            ParamType.appendTo(appender);
            if (String name := hasName())
                {
                appender.add(' ')
                        .add(name);
                }
            }
        }

    /**
     * Represents a function return value.
     */
    interface Return<ReturnType>
            extends immutable Const
        {
        /**
         * The ordinal index of the return value.
         */
        @RO Int ordinal;

        /**
         * Determine the return value name.
         *
         * @return True iff the return value has a name
         * @return (conditional) the return value name
         */
        conditional String hasName();

        @Override
        Int estimateStringLength()
            {
            Int len = ReturnType.estimateStringLength();
            if (String name := hasName())
                {
                len += 1 + name.size;
                }
            return len;
            }

        @Override
        void appendTo(Appender<Char> appender)
            {
            ReturnType.appendTo(appender);
            if (String name := hasName())
                {
                appender.add(' ')
                        .add(name);
                }
            }
        }

    /**
     * SourceCodeInfo provides information about the name of the file that contains source code,
     * and the 0-based line number within that file that the relevant source code begins.
     */
    const SourceCodeInfo(String sourceFile, Int lineNumber);
    }