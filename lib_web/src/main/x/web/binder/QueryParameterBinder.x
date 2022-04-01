import ecstasy.reflect.Parameter;

import web.QueryParam;

/**
 * A parameter binder that binds values from an HTTP request's URI query parameters.
 */
const QueryParameterBinder
        implements ParameterBinder<HttpRequest>
    {
    @Override
    <ParamType> BindingResult<ParamType> bind(Parameter<ParamType> parameter, HttpRequest request)
        {
        if (parameter.is(QueryParam))
            {
            String name = "";
            if (parameter.is(ParameterBinding))
                {
                name = parameter.templateParameter;
                }
            if (name == "")
                {
                assert name := parameter.hasName();
                }

            import HttpRequest.QueryParameter;

            Map<String, QueryParameter> queryParamMap = request.parameters;

            // ToDo: this process is actually a lot more complex, e.g. type conversion
            if (QueryParameter param := queryParamMap.get(name))
                {
                String value = param.is(String) ? param : param[0];
                assert value.is(ParamType);
                return new BindingResult(value, True);
                }
            }
        return new BindingResult();
        }
    }