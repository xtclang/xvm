/**
 * A Method represents a method of a particular class or type. A method has a name, a number of
 * parameter types, and a number of return types. A method can be bound to a particular target (of
 * a type containing the method) in order to obtain an invocable function.
 */
const Method<TargetType,
             ReturnTypes extends Tuple<ReturnTypes...>,
             ParamTypes extends Tuple<ParamTypes...>>
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

    // -----

    /**
     * If this method represents a property, return that information, otherwise {@code null}.
     *
     * TODO note about compile time types
     */
    @lazy Property? property.get()
        {
        if (ReturnTypes.size == 1 && ReturnTypes[0].isA(Ref) && ParamTypes.size == 0)
            {
            for (Method getter : ReturnTypes[0].methodsByName["get"])
                {
                if (getter.ReturnTypes.size == 1 && getter.ParamTypes.size == 0)
                    {
                    return new Property<ReturnTypes[0]>(name);
                    }
                }

            assert false;
            }

        return null;
        }

    /**
     * Determine if this method _consumes_ that type.
     *
     * A method _m_ "consumes" type _T_ if any of the following holds true:
     * 1. _m_ has a parameter type declared as _T_;
     * 2. _m_ has a return type that _"consumes T"_;
     * 3. _m_ has a parameter type that _"produces T"_.
     *
     * There is a notable exception to the above rule #2 for a method on a type corresponding to a
     * property, which returns a {@code Ref}) to represent the property type, and thus (due to the
     * the methods on {@code Ref<T>}) appears to _"consume T"_; however, if the type containing
     * the property is explicitly immutable, or the method returning the {@code Ref<T>}) is
     * annotated with {@code @ro}/{@code ReadOnly}, then _m_ is assumed to not _"consume T"_.
     * TODO implement the above exception to the rule
     */
    Boolean consumes(Type that)
        {
        for (Type param : ParamTypes)
            {
            if (param == that || param.produces(that))
                {
                return true;
                }
            }

        for (Type return_ : ReturnTypes)
            {
            if (return_.consumes(that))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if this method _produces_ that type.
     *
     * A method _m_ "produces" type _T_ if any of the following holds true:
     * 1. _m_ has a return type declared as _T_;
     * 2. _m_ has a return type that _"produces T"_;
     * 3. _m_ has a parameter type that _"consumes T"_.
     */
    Boolean produces(Type that)
        {
        for (Type return_ : ReturnTypes)
            {
            if (return_ == that || return_.produces(that))
                {
                return true;
                }
            }

        for (Type param : ParamTypes)
            {
            if (param.consumes(that))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * Determine if this method could act as a substitute for the specified method.
     *
     * @see Type.isA
     */
    Boolean isSubstitutableFor(Method that)
        {
        if (this == that)
            {
            return true;
            }

        // TODO
        /*
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
         *          2. _t1_ produces _p1_
         *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
         *       _m1_ and _r2_ of _m2_, the following holds true:
         *      1. _r2_ is assignable to _r1_
         * 2. if _t1_ is explicitly immutable, then _t2_ must also be explicitly immutable.
         */

        return true;
        }


    // ----- dynamic behavior ----------------------------------------------------------------------

    /**
     * Given an object reference of a type that contains this method, obtain the invocable function
     * that corresponds to this method on that object.
     */
    Function<ReturnTypes, ParamTypes> bindTarget(TargetType target)
        {
        TODO this can not be abstract .. maybe "&target.getFunction(this)" or something like that?
        }

    /**
     * Given an object reference of a type that contains this method, invoke that method passing
     * the specified arguments, and returning the results.
     */
    ReturnType invoke(TargetType target, ParamType args)
        {
        return bindTarget(target).invoke(args);
        }
    }
