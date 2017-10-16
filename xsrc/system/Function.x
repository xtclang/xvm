/**
 * TODO
 */
interface Function<ParamTypes extends Tuple<Type...>, ReturnTypes extends Tuple<Type...>>
// interface Function<(ParamTypes), (ReturnTypes)>
    {
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
//     TODO
//    Function bind(Parameter param, param.ParamType value)
//        {
//        return bind(Map:{param=value});
//        }

    /**
     * Binds any number of parameters of the Function, resulting in a new Function that does not
     * contain any of those parameters.
     */
    Function bind(Map<Parameter, Object> params)
        {
        if (params.empty)
            {
            return this;
            }

        for (Map.Entry<Parameter, Object> entry : params)
            {

            }
        return new PartiallyBound();
        }

    /**
     * Invokes the function passing the specified arguments as a Tuple that matches the function's
     * {@code ParamTypes}, and returns a Tuple that matches the function's {@code ReturnTypes}.
     */
    ReturnTypes invoke(ParamTypes args);

    /**
     * Invoke the function with the specified arguments, obtaining a future result. If the function
     * is not a service invocation, or at the discretion of the runtime, then the function will be
     * executed in a synchronous manner, and the future will have completed by the time that this
     * method returns.
     */
    @Future ReturnTypes invokeService(ParamTypes args);

    /**
     * Override the automatic conversion-to-function method from Object, removing the "@Auto"
     * annotation, so that functions do not try to automatically convert themselves to functions.
     */
    @Override
    function Function() to<function Function()>()
        {
        return super();
        }

    // -----

    private class PartiallyBound<ReturnTypes extends Tuple<ReturnTypes...>, ParamTypes extends Tuple<ParamTypes...>>
            implements Function<ReturnTypes, ParamTypes>
        {
        construct PartiallyBound(Function fn, Map<Parameter, Object> params)
            {
            }
        }

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
