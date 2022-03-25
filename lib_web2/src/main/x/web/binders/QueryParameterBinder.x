import ecstasy.reflect.Parameter;

import web.QueryParam;

/**
 * A parameter binder that binds values from an HTTP request's URI query parameters.
 */
const QueryParameterBinder
        implements ParameterBinder<Request>
    {
    @Override
    <ParamType> BindingResult<ParamType> bind(Parameter<ParamType> parameter, Request request)
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

            Map<String, List<String>> queryParamMap = request.parameters;
            // ToDo: this process is actually a lot more complex, e.g. type conversion
            if (List<String> list := queryParamMap.get(name), !list.empty)
                {
                return new BindingResult<ParamType>(list[0].as(ParamType), True);
                }
            }
        return new BindingResult();
        }
    }