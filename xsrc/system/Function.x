import collections.ListMap;
import reflect.MethodTemplate;

/**
 * A Function represents a unit of invocation. A function (usually) has a name, parameters, and
 * return values.
 *
 * To bind (or partially bind) a function, use one of the [bind] methods. This is also referred to
 * as _partial application_ of a function.
 *
 * To invoke a function, use [invoke], passing a compatible tuple of arguments. To invoke a
 * function that permits asynchronous execution (if the function represents something that executes
 * within the scope of another service), use [invokeAsync] instead.
 */
interface Function<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends Hashable
    {
    /**
     * Represents a function parameter, including type parameters.
     */
    static interface Parameter<ParamType>
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
        }

    /**
     * Represents a function return value.
     */
    static interface Return<ReturnType>
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
        }

    /**
     * A UnboundFormalParameter exception is raised when an attempt is made to bind a function
     * parameter or invoke a function with parameters without having successfully bound all of the
     * formal type parameters.
     */
    static const UnboundFormalParameter(String? text = null, Exception? cause = null)
            extends IllegalState(text, cause);

    /**
     * The function's name.
     */
    @RO String name;

    /**
     * The parameters, by ordinal.
     *
     * If this function represents a partially bound function, then the already-bound parameters
     * will not be present in this value.
     */
    @RO Parameter[] params;

    /**
     * Find a parameter by the provided name.
     *
     * @param name  the name of the parameter to find
     *
     * @return True iff a parameter with the specified name was found
     * @return (conditional) the parameter
     */
    conditional Parameter findParam(String name)
        {
        return params.iterator().untilAny(p ->
            {
            if (String s := p.hasName())
                {
                return s == name;
                }
            return False;
            });
        }

    /**
     * The return values, by ordinal.
     */
    @RO Return[] returns;

    /**
     * Find a return value by the provided name.
     *
     * @param name  the name of the return value to find
     *
     * @return True iff a return value with the specified name was found
     * @return (conditional) the return value
     */
    conditional Return findReturn(String name)
        {
        return returns.iterator().untilAny(r ->
            {
            if (String s := r.hasName())
                {
                return s == name;
                }
            return False;
            });
        }

    /**
     * Determine if the function return value is a _conditional return_. A conditional return is a
     * Tuple of at least two elements, whose first element is a Boolean; when the Boolean value is
     * False, the remainder of the expected return values are absent.
     */
    @RO Boolean conditionalResult;

    /**
     * Binds a single parameter of the Function, resulting in a new Function that does not contain
     * that parameter.
     *
     * @param param  a parameter of this function
     * @param value  a corresponding argument value
     *
     * @return the new function that results from binding the specified parameter
     */
    <ParamType> Function!<> bind(Parameter<ParamType> param, ParamType value);

    /**
     * Binds any number of parameters of the Function, resulting in a new Function that does not
     * contain any of those parameters.
     *
     * @param params  a map from parameter to argument value for each parameter to bind
     *
     * @return a function that represents the result of binding the specified parameters
     *
     * @throws IllegalArgument  if a parameter is specified that cannot be found on this function
     * @throws TypeMismatch  if an argument value is not the type required for the corresponding
     *         parameter
     * @throws UnboundFormalParameter  if an attempt is made to bind a parameter that depends on
     *         formal type parameters before all of those formal type parameters have been bound
     */
    Function!<> bind(Map<Parameter, Object> params);

    /**
     * Invokes the function passing the specified arguments as a Tuple that matches the function's
     * `ParamTypes`, and returns a Tuple that matches the function's `ReturnTypes`.
     *
     * @param args  a tuple of the arguments to invoke the function
     *
     * @return a tuple of the return values from the function
     *
     * @throws UnboundFormalParameter  if an attempt is made to bind a non-formal parameter before
     *         all of the formal type parameters have been bound
     */
    @Op("()")
    ReturnTypes invoke(ParamTypes args);

    /**
     * Determine if the function represents a service invocation. Service invocations have the
     * _potential_ for asynchronous execution.
     */
    @RO Boolean futureResult;

    /**
     * Invoke the function with the specified arguments, obtaining a future result. It is possible
     * that the function will be executed in a synchronous manner and that the future will have
     * completed by the time that this method returns; this will occur, for example, if the function
     * does not actually represent a service invocation, or if the runtime chooses to execute a
     * service invocation synchronously.
     *
     * @param args  a tuple of the arguments to invoke the function
     *
     * @return a future tuple of the return values from the function
     */
    FutureVar<ReturnTypes> invokeAsync(ParamTypes args);

    /**
     * Obtain the template that defines the function, if it is available.
     *
     * @return True iff the function can provide a method template for itself
     * @return (conditional) the method template
     */
    conditional MethodTemplate hasTemplate();
    }
