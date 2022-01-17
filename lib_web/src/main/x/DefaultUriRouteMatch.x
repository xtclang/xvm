import ecstasy.reflect.Parameter;

/**
 * A default implementation of UriRouteMatch.
 */
class DefaultUriRouteMatch
        implements UriRouteMatch
    {
    construct(UriMatchInfo info, UriRoute route)
        {
        this.info           = info;
        this.route          = route;
        this.variableValues = info.variableValues;
        }

    construct(UriMatchInfo info, UriRoute route, Map<String, Object> variableValues)
        {
        this.info           = info;
        this.route          = route;
        this.variableValues = variableValues;
        }

    public/private UriMatchInfo info;

    @Override
    public/private Map<String, Object> variableValues;

    @Override
    public/private UriRoute route;

    @Override
    MediaType[] produces.get()
        {
        return route.produces;
        }

    @Override
    Function<Tuple, Tuple> fn.get()
        {
        return route.executable.fn;
        }

    @Override
    Boolean conditionalResult.get()
        {
        return route.executable.conditionalResult;
        }

    @Override
    String uri.get()
        {
        return info.uri;
        }

    @Override
    List<UriMatchVariable> variables.get()
        {
        return info.variables;
        }

    @Override
    Map<String, UriMatchVariable> variableMap.get()
        {
        return info.variableMap;
        }

    @Override
    Boolean canConsume(MediaType? mediaType)
        {
        if (route.consumes.size > 0 && mediaType.is(MediaType))
            {
            return mediaType == MediaType.ALL_TYPE || explicitlyConsumes(mediaType);
            }
        else
            {
            return True;
            }
        }

    @Override
    Boolean explicitlyConsumes(MediaType contentType)
        {
        return route.consumes.contains(contentType);
        }

    @Override
    Boolean canProduce(MediaType[] acceptableTypes)
        {
        return acceptableTypes.size == 0 || anyMediaTypesMatch(route.produces, acceptableTypes);
        }

    private Boolean anyMediaTypesMatch(MediaType[] producedTypes, MediaType[] acceptableTypes)
        {
        if (acceptableTypes.size == 0)
            {
            return True;
            }

        if (producedTypes.contains(MediaType.ALL_TYPE))
            {
            return True;
            }

        for (MediaType acceptableType : acceptableTypes)
            {
            if (acceptableType == MediaType.ALL_TYPE || producedTypes.contains(acceptableType))
                {
                return True;
                }
            }

        return False;
        }

    @Override
    Tuple execute(Map<String, Object> parameterValues)
        {
        Tuple parameters = Tuple:();

        if (fn.params.size == 0)
            {
            return fn.invoke(parameters);
            }

        for (Parameter param : fn.params)
            {
            String name = "";
            if (param.is(ParameterBinding))
                {
                name = param.templateParameter;
                }

            if (name == "")
                {
                assert name := param.hasName();
                }

            if (Object paramValue := parameterValues.get(name))
                {
                parameters = parameters.add(convert(param, paramValue));
                }
            else if (Object variableValue := variableValues.get(name))
                {
                parameters = parameters.add(convert(param, variableValue));
                }
            else if (Object defaultValue := param.defaultValue())
                {
                parameters = parameters.add(defaultValue);
                }
            }

        return fn.invoke(parameters);
        }

    private Object convert(Parameter parameter, Object value)
        {
        // ToDo: add conversion logic when we have a converter registry
        return value;
        }

    @Override
    UriRouteMatch newFulfilled(Map<String, Object> values, List<Parameter> parameters)
        {
        return new FulfilledUriRouteMatch(info, route, values, parameters);
        }

    /**
     * A DefaultUriRouteMatch instance that has its parameters bound to values.
     */
    static class FulfilledUriRouteMatch
            extends DefaultUriRouteMatch
        {
        construct (UriMatchInfo        info,
                   UriRoute            route,
                   Map<String, Object> variableValues,
                   List<Parameter>     requiredParameters)
            {
            construct DefaultUriRouteMatch(info, route, variableValues);
            }

        @Override
        Tuple<Object> execute()
            {
            return execute(variableValues);
            }
        }
    }