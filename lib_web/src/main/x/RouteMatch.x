import ecstasy.reflect.Parameter;

/**
 * An executable route that matches a URI.
 */
interface RouteMatch
        extends ExecutableFunction
    {
    /**
     * The specific media types produced by this route.
     */
    @RO MediaType[] produces;

    /**
     * The variable values following a successful match.
     */
    @RO Map<String, Object> variableValues;

    /**
     * The parameter that maps to a request body.
     */
    @RO Parameter? bodyParameter;

    /**
     * Returns the required parameters for this RouteMatch.
     *
     * Note that this is not the same as the function's params, it will include
     * a subset of the function params, excluding those that have been subtracted
     * from the URI variables.
     *
     * @return the required parameters in order to invoke this route
     */
    @RO List<Parameter> requiredParameters.get()
        {
        Parameter[]     parameters         = this.fn.params;
        List<Parameter> requiredParameters = new Array(parameters.size);
        if (parameters.size > 0)
            {
            Map<String, Object> matchVariables = this.variableValues;
            if (matchVariables.empty)
                {
                requiredParameters.addAll(parameters);
                }
            else
                {
                for (Parameter parameter : parameters)
                    {
                    if (String name := parameter.hasName(), !matchVariables.contains(name))
                        {
                        requiredParameters.add(parameter);
                        }
                    }
                }
            }
        return requiredParameters;
        }

    /**
     * Determine whether the specified content media type is an accepted type.
     *
     * @param contentType the content type
     * @return True if the media type is accepted
     */
    Boolean canConsume(MediaType? mediaType);

    /**
     * Determine whether the specified content type is an explicitly accepted type.
     *
     * @param contentType the content type
     *
     * @return True if the content type is an explicitly accepted type
     */
    Boolean explicitlyConsumes(MediaType contentType);

    /**
     * Determine whether the route produces any of the given media types.
     *
     * @param acceptableTypes the acceptable media types
     * @return True if the route produces any of the given media types
     */
    Boolean canProduce(MediaType[] acceptableTypes);

    /**
     * Determine whether the route produces the given media type.
     *
     * @param acceptableType the acceptable media type
     * @return True if the route produces the given media type
     */
    Boolean canProduce(MediaType acceptableType)
        {
        return canProduce([acceptableType]);
        }

    /**
     * Returns a RouteMatch that is this RouteMatch with its variables
     * fulfilled and bound to values from the specified arguments
     *
     * @return the fulfilled RouteMatch
     */
    RouteMatch! fulfill(Map<String, Object> arguments)
        {
        if (arguments.size == 0)
            {
            return this;
            }

        Map<String, Object> newVariables          = new ListMap();
        Parameter[]         parameters            = fn.params;
        Boolean             hasRequiredParameters = requiredParameters.empty;
        Parameter?          bodyParameter         = bodyParameter;
        String?             bodyParameterName     = Null;

        if (bodyParameter.is(Parameter), String name := bodyParameter.hasName())
            {
            bodyParameterName = name;
            }

        newVariables.putAll(this.variableValues);

        for (Parameter requiredParameter : parameters)
            {
            if (String argumentName := requiredParameter.hasName(),
                Object value        := arguments.get(argumentName))
                {
                if (bodyParameterName != Null && bodyParameterName == argumentName)
                    {
                    requiredParameter = bodyParameter.as(Parameter);
                    }

                if (hasRequiredParameters)
                    {
                    requiredParameters.remove(requiredParameter);
                    }

                newVariables.put(argumentName, value);
                }
            }

        return newFulfilled(newVariables, requiredParameters);
        }

    RouteMatch newFulfilled(Map<String, Object> variables, List<Parameter> parameters);

    /**
     * Execute the route.
     *
     * @return the execution result
     */
    Tuple execute()
        {
        return execute(Map<String, Object>:[]);
        }

    /**
     * Execute the route with the given values.
     * The passed in map should contain values for every parameter in the
     * requiredParameters property.
     *
     * @param parameterValues the parameter values
     *
     * @return the execution result
     */
    Tuple execute(Map<String, Object> parameterValues);
    }