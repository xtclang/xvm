/**
 * A Function represents a unit of invocation. A function has a name, a number of
 * parameter types, and a number of return types.
 */
interface Function<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends Hashable
    {
    /**
     * The function name (could be empty).
     */
    @RO String name;

    @RO Parameter[] params;
    @RO Map<String, Parameter> paramsByName;

    @RO Return[] returns;
    @RO Map<String, Return> returnsByName;

    /**
     * Determine if the function return value is a conditional return. A conditional return is a
     * Tuple of at least two elements, whose first element is a Boolean, and whose
     */
    @RO Boolean conditionalResult;

    /**
     * Determine if the function represents a service invocation. Service invocations have the
     * _potential_ for asynchronous execution.
     */
    @RO Boolean futureResult;

    /**
     * Binds a single parameter of the Function, resulting in a new Function that does not contain
     * that parameter.
     */
    // TODO: Name "param" is unresolvable. ("param.ParamType")
    // Function!<> bind(Parameter param, param.ParamType value);

    /**
     * Binds any number of parameters of the Function, resulting in a new Function that does not
     * contain any of those parameters.
     */
    Function!<> bind(Map<Parameter, Object> params);

    /**
     * Invokes the function passing the specified arguments as a Tuple that matches the function's
     * `ParamTypes`, and returns a Tuple that matches the function's `ReturnTypes`.
     */
    ReturnTypes invoke(ParamTypes args);

    /**
     * Invoke the function with the specified arguments, obtaining a future result. If the function
     * is not a service invocation, or at the discretion of the runtime, then the function will be
     * executed in a synchronous manner, and the future will have completed by the time that this
     * method returns.
     */
    FutureVar<ReturnTypes> invokeService(ParamTypes args);

    /**
     * TODO move to own file?
     */
    interface Parameter<ParamType>
        {
        @RO Int ordinal;
        conditional String name();
        conditional ParamType defaultValue();
        }

    /**
     * TODO move to own file?
     */
    interface Return<ReturnType>
        {
        @RO Int ordinal;
        conditional String name();
        }
    }
