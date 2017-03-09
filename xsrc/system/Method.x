const Method<TargetType,
             ParamTypes extends Tuple,
             ReturnTypes extends Tuple>
    {
    /**
     * The
     */
    MethodSignature signature;

    /**
     * The method's name.
     */
    String name;

    /**
     * The method's return values, zero or more.
     */
    ReturnValue[] returnValue;

    Boolean property;

    /**
     * True if the method has a conditional return. A conditional return is one that conditionally
     * has a value. As such, the return value has one additional Boolean value prepended to the
     * return tuple, and the remainder of the tuple is only available iff the Boolean value is True.
     * An attempt to access the remainder of the values in the return tuple will result in an
     * exception if the Boolean value (the first element of the tuple) is False.
     */
    Boolean conditionalReturn;

    /**
     * The type parameters for the method. A method may have zero or more type parameters, which
     * allow a method to represent multiple implementations, with the most appropriate implementation
     * selected based on the type parameters specified by the method invocation.
     */
    TypeParam[] typeParam;

    Type
    /**
     * TODO
     */
    InvocationParam[] invokeParam;

    Function bindTarget(TargetType target);

    ReturnType invoke(TargetType target, TypeParamType typeParams, ParamType args)
        {
        Method method = this;
        for (
        if (typeParams.length > 0)
            {
            return bindTypeParam(typeParams).invoke(target, (), args);
            }
        else
            {
            return bindParam(args)();
            }
        Function fn =
        }
    }
