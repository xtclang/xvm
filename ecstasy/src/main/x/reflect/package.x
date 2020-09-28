/**
 * This package provides the reflection implementation for the Ecstasy language.
 */
package reflect
    {
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
    enum Access(String keyword)
        {
        Public   ("public"),
        Protected("protected"),
        Private  ("private"),
        Struct   ("struct")
        }

    /**
     * Represents an Ecstasy Package.
     *
     * Because of its name, the Package type must be defined inside (textually included
     * in) the this "package.x" file, because the file name "package.x" is reserved for
     * defining the package itself, while in this case we are defining the "Package" type.
     */
    interface Package
            extends immutable Const
        {
        /**
         * Test to see if this package represents a module import and if so, return it.
         *
         * @return True iff this package imports a module
         * @return (conditional) the [Module] that this package imports
         */
        conditional immutable Module isModuleImport();

        /**
         * The classes contained immediately within this package.
         */
        @RO immutable Class[] classes.get()
            {
            return classByName.values.toArray(Constant).as(immutable Class[]);
            }

        /**
         * A mapping from simple name to class within this package.
         */
        @RO immutable Map<String, Class> classByName;


        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        Int estimateStringLength()
            {
            return &this.actualClass.name.size;
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            return &this.actualClass.name.appendTo(buf);
            }
        }

    /**
     * Represents an Ecstasy Module, which is the outer-most level organizational unit for
     * source code, and the aggregate unit for compiled code distribution and deployment.
     *
     * Because of its name, the Module type must be defined inside (textually included in)
     * this "package.x" file, because the Module.x file would appear to be a separate Ecstasy
     * module, because the file name "module.x" is reserved for defining the module itself, while
     * in this case we are defining the "Module" type.
     */
    interface Module
            extends Package
        {
        /**
         * The simple qualified name of the module, such as "ecstasy".
         */
        @RO String simpleName.get()
            {
            return qualifiedName.split('.')[0];
            }

        /**
         * The fully qualified name of the module, such as "ecstasy.xtclang.org".
         */
        @RO String qualifiedName.get()
            {
            return &this.actualClass.name;
            }

        /**
         * The version of the module, if the version is known.
         */
        @RO Version? version;

        /**
         * The modules that this module depends on by linkage, both directly and indirectly. For
         * each such module that this module is linked to, and that is also visible within this
         * module's namespace, the shortest dot-delimited path and the depended-upon module will
         * be present in the map. In the case of an unlinked, optional module (one that is specified
         * as a "desired" or "optional" import, but was not loaded and linked with this module for
         * whatever reason), no entry will be present in the map.
         */
        @RO immutable Map<String, Module!> modulesByPath;

        /**
         * Given the qualified name of a class nested within this module, obtain the [Class].
         *
         * @return True iff the name identified a class
         * @return (conditional) the specified [Class]
         */
        conditional Class classForName(String name);

        /**
         * Given the qualified name of a type nested within this module, obtain the [Type].
         *
         * @return True iff the name identifies a type
         * @return (conditional) the specified [Type]
         */
        conditional Type typeForName(String name);


        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        Int estimateStringLength()
            {
            return qualifiedName.size;
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            return qualifiedName.appendTo(buf);
            }
        }

    /**
     * Represents the information about an annotation, including the template representing the
     * annotation, and the argument values for the annotation.
     */
    const AnnotationTemplate(ClassTemplate template, Argument[] arguments);

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
        Appender<Char> appendTo(Appender<Char> buf)
            {
            ParamType.appendTo(buf);
            if (String name := hasName())
                {
                buf.add(' ')
                   .addAll(name);
                }
            return buf;
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
        Appender<Char> appendTo(Appender<Char> buf)
            {
            ReturnType.appendTo(buf);
            if (String name := hasName())
                {
                buf.add(' ')
                   .addAll(name);
                }
            return buf;
            }
        }

    /**
     * SourceCodeInfo provides information about the name of the file that contains source code,
     * and the 0-based line number within that file that the relevant source code begins.
     */
    const SourceCodeInfo(String sourceFile, Int lineNumber);
    }