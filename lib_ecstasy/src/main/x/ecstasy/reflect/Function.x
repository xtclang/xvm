/**
 * A Function represents a unit of invocation. A function (usually) has a name, parameters, and
 * return values.
 *
 * To bind (or partially bind) a function, use one of the [bind] methods. This is also referred to
 * as _partial application_ of a function.
 *
 * To invoke a function, use [invoke], passing a compatible tuple of arguments. To invoke a
 * function that permits asynchronous execution (if the function represents something that executes
 * within the scope of another service), use [invoke] assigning the result into a [@Future Tuple].
 */
interface Function<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends Signature<ParamTypes, ReturnTypes>
    {
    // ----- dynamic invocation support ------------------------------------------------------------

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
     * Performs an operation that is inverse to the "bind" on a function.
     *
     * @return True if this Function can be "unbound" to a Function that [has a template](hasTemplate)
     * @return (optional) the MethodTemplate for the Function
     * @return (optional) the Function that [has a template](hasTemplate)
     * @return (optional) a map of parameter values
     */
    conditional (MethodTemplate, Function!<>, Map<Parameter, Object>) isFunction();

    /**
     * Performs an operation that is inverse to the "bind" on a method, potentially followed by
     * a bind on the resulting Function.
     *
     * @return True if this Function can be "unbound" to a Method that [has a template](hasTemplate)
     * @return (optional) the target reference
     * @return (optional) the Method for that target that [has a template](hasTemplate)
     * @return (optional) a map of parameter values
     */
    <Target> conditional (Target, Method<Target>, Map<Parameter, Object>) isMethod();
    }
