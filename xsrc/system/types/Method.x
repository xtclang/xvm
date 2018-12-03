import collections.HashSet;

/**
 * A Method represents a method of a particular class or type. A method has a name, a number of
 * parameter types, and a number of return types. A method can be bound to a particular target (of
 * a type containing the method) in order to obtain an invocable function.
 */
const Method<TargetType, ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
    {
    /**
     * The method's name.
     */
    String name;

    /**
     * True if the method has a conditional return. A conditional return is one that conditionally
     * has a value. As such, the return value has one additional Boolean value prepended to the
     * return tuple, and the remainder of the tuple is only available iff the Boolean value is True.
     * An attempt to access the remainder of the values in the return tuple will result in an
     * exception if the Boolean value (the first element of the tuple) is False.
     */
    Boolean conditionalReturn;

    enum Access {Public, Protected, Private}

    /**
     * Method access.
     */
    Access access;

    // -----

    /**
     * If this method represents a property, return that information, otherwise {@code null}.
     *
     * TODO note about compile time types
     */
    @Lazy Property? property.calc()
        {
        if (ReturnTypes.size == 1 && ReturnTypes[0].isA(Ref) && ParamTypes.size == 0)
            {
            if (MultiMethod getters : ReturnTypes[0].methodsByName.get("get"))
                {
                for (Method<> getter : getters.methods)
                    {
                    if (getter.ReturnTypes.size == 1 && getter.ParamTypes.size == 0)
                        {
                        return new Property /* TODO <ReturnTypes[0]> */ (
                            getter.as(Method<Object, Tuple<>, Tuple<Ref>>));
                        }
                    }
                }

            assert false;
            }

        return null;
        }

    /**
     * Determine if this method _consumes_ a formal type with the specified name.
     *
     * A method _m_ "consumes" type _T_ if any of the following holds true:
     * 1. _m_ has a parameter type declared as _T_;
     * 2. _m_ has a parameter type that _"produces T"_.
     * 3. _m_ has a return type that _"consumes T"_;
     *
     * There is a notable exception to the above rule #3 for a method on a type corresponding to a
     * property, which returns a {@code Ref}) to represent the property type, and thus (due to the
     * the methods on {@code Ref<T>}) appears to _"consume T"_; however, if the type containing
     * the property is explicitly immutable, or the method returning the {@code Ref<T>}) is
     * annotated with {@code @RO}/{@code ReadOnly}, then _m_ is assumed to not _"consume T"_.
     */
    Boolean consumesFormalType(String typeName, Boolean ignoreImmediateProduction = false)
        {
        paramLoop: for (Type paramType : ParamTypes)
            {
            if (String[] names : formalParamNames(paramLoop.count))
                {
                if (!ignoreImmediateProduction && names.contains(typeName))
                    {
                    return true;
                    }
                }
            else
                {
                if (paramType.producesFormalType(typeName))
                    {
                    return true;
                    }
                }
            }

        returnLoop: for (Type returnType : ReturnTypes)
            {
            if (String[] names : formalReturnNames(returnLoop.count))
                {
                // may produce, but doesn't consume
                }
            else
                {
                // see the exception of the rule 3 above
                ignoreImmediateProduction = property?.readOnly : false;

                if (returnType.consumesFormalType(typeName, ignoreImmediateProduction))
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    /**
     * Determine if this method _produces_ a formal type with the specified name.
     *
     * A method _m_ "produces" type _T_ if any of the following holds true:
     * 1. _m_ has a return type declared as _T_;
     * 2. _m_ has a return type that _"produces T"_;
     * 3. _m_ has a parameter type that _"consumes T"_.
     */
    Boolean producesFormalType(String typeName)
        {
        returnLoop: for (Type returnType : ReturnTypes)
            {
            if (String[] names : formalReturnNames(returnLoop.count))
                {
                if (names.contains(typeName))
                    {
                    return true;
                    }
                }
            else
                {
                if (returnType.producesFormalType(typeName))
                    {
                    return true;
                    }
                }
            }

        paramLoop: for (Type paramType : ParamTypes)
            {
            if (String[] names : formalParamNames(paramLoop.count))
                {
                // may produce, but doesn't consume
                }
            else
                {
                if (paramType.consumesFormalType(typeName))
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    /**
     * Determine if this method could act as a substitute for the specified method.
     *
     * @see Type.isA
     */
    Boolean isSubstitutableFor(Method!<> that)
        {
        if (this == that)
            {
            return true;
            }

        /*
         * Excerpt From Type#isA() documentation (where m2 == this and m1 == that):
         *
         * Type _T2_ is assignable to a Type _T1_ iff both of the following hold true:
         * 1. for each _m1_ in _M1_, there exists an _m2_ in _M2_ for which all of the following hold
         *    true:
         *    1. _m1_ and _m2_ have the same name
         *    2. _m1_ and _m2_ have the same number of parameters, and for each parameter type _p1_ of
         *       _m1_ and _p2_ of _m2_, at least one of the following holds true:
         *       1. _p1_ is assignable to _p2_
         *       2. both _p1_ and _p2_ are (or are resolved from) the same type parameter, and both of
         *          the following hold true:
         *          1. _p2_ is assignable to _p1_
         *          2. _T1_ produces _p1_
         *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
         *       _m1_ and _r2_ of _m2_, the following holds true:
         *      1. _r2_ is assignable to _r1_
         */

        if (this.name             != that.name            ||
            this.ParamTypes.size  != that.ParamTypes.size ||
            this.ReturnTypes.size != that.ReturnTypes.size)
            {
            return false;
            }

        Iterator<Type> iterR1 = that.ReturnTypes.iterator();
        for (Type typeR2 : this.ReturnTypes)
             {
             assert Type typeR1 : iterR1.next();
             if (!typeR2.isA(typeR1))
                 {
                 return false;
                 }
             }

        Iterator<Type> iterP1 = that.ParamTypes.iterator();
        loop: for (Type typeP2 : this.ParamTypes)
            {
            assert Type typeP1 : iterP1.next();
            if (typeP1.isA(typeP2))
                {
                continue;
                }

            if (!typeP2.isA(typeP1))
                {
                return false;
                }

            // if there is an number of different formal names, then at least one of them must be
            // produced by the type T1
            if (String[] namesThis : this.formalParamNames(loop.count))
                {
                Set<String> setThis = new HashSet(namesThis);

                if (String[] namesThat : that.formalParamNames(loop.count))
                    {
                    Set<String> setThat = new HashSet(namesThat);

                    setThat := setThat.retainAll(setThis);
                    for (String name : setThat)
                        {
                        if (that.TargetType.producesFormalType(name))
                            {
                            return true;
                            }
                        }
                    }
                }
            return false;
            }

        return true;
        }

    /**
     * Return an array of formal type names for the parameter type at the specified index.
     * If the parameter type is not a formal one, this method will return false.
     */
    conditional String[] formalParamNames(Int i)
         {
         TODO -- native
         }

    /**
     * Return an array of formal type names for the return type at the specified index.
     * If the return type is not a formal one, this method will return false.
     */
    conditional String[] formalReturnNames(Int i)
         {
         TODO -- native
         }

    // ----- dynamic behavior ----------------------------------------------------------------------

    /**
     * Given an object reference of a type that contains this method, obtain the invocable function
     * that corresponds to this method on that object.
     */
    Function<ParamTypes, ReturnTypes> bindTarget(TargetType target)
        {
        // this can not be abstract .. maybe "&target.getFunction(this)" or something like that?
        TODO;
        }

    /**
     * Given an object reference of a type that contains this method, invoke that method passing
     * the specified arguments, and returning the results.
     */
    ReturnTypes invoke(TargetType target, ParamTypes args)
        {
        return bindTarget(target).invoke(args);
        }
    }
