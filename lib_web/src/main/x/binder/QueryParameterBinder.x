import ecstasy.reflect.Parameter;

import web.QueryParam;

/**
 * A parameter binder that binds values from a http request's URI query parameters.
 */
const QueryParameterBinder
        implements ParameterBinder<HttpRequest>
    {
    @Override
    <ParamType> BindingResult<ParamType> bind(Parameter<ParamType> parameter, HttpRequest request)
        {
        Parameter queryParam = parameter;
        if (queryParam.is(QueryParam))
            {
            String name = "";
            if (queryParam.is(ParameterBinding))
                {
                name = queryParam.templateParameter;
                }
            if (name == "")
                {
                assert name := parameter.hasName();
                }

            Map<String, List<String>> queryParamMap = request.parameters;
            // ToDo: this process is actually a lot more complex, e.g. type conversion
            if (List<String> list := queryParamMap.get(name))
                {
                if (!list.empty)
                    {
                    return new BindingResult<ParamType>(list[0].as(ParamType), True);
                    }
                }
            }
        return new BindingResult();
        }
    }