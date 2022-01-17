import ecstasy.reflect.Parameter;

/**
 * A ParameterBinderRegistry containing ParameterBinder instances that
 * bind parameters to values from a from a HttpRequest.
 */
class RequestBinderRegistry
        implements ParameterBinderRegistry<HttpRequest>
    {
    construct()
        {
        binders = new Array();
        binders.add(new QueryParameterBinder());
        }

    private Array<ParameterBinder<HttpRequest>> binders;

    @Override
    void addParameterBinder(ParameterBinder<HttpRequest> binder)
        {
        binders.add(binder);
        }

    @Override
    conditional ParameterBinder<HttpRequest>
            findParameterBinder(Parameter parameter, HttpRequest source)
        {
        ParameterBinder<HttpRequest>? binder = Null;
        for (ParameterBinder<HttpRequest> pb : binders)
            {
            if (pb.canBind(parameter))
                {
                if (binder == Null || binder.priority < pb.priority)
                    {
                    binder = pb;
                    }
                }
            }

        return binder == Null ? False : (True, binder);
        }

    /**
     * Produce the bound parameters for the route and request.
     *
     * @param route  the route to bind parameters for
     * @param req    the http request to obtain parameters values from
     *
     * @return a RouteMatch bound to the arguments from the source request
     */
    RouteMatch bind(RouteMatch route, HttpRequest req)
        {
        Map<String, Object> arguments = new HashMap();
        for (Parameter p : route.requiredParameters)
            {
            if (String                       name   := p.hasName(),
                ParameterBinder<HttpRequest> binder := findParameterBinder(p, req))
                {
                BindingResult result = binder.bind(p, req);
                if (result.bound)
                    {
                    arguments.put(name, result.value);
                    }
                }
            }
        return route.fulfill(arguments);
        }
    }
