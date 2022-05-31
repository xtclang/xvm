import ecstasy.reflect.Parameter;

/**
 * A ParameterBinderRegistry containing ParameterBinder instances that
 * bind parameters to values from a from a HttpRequest.
 */
class RequestBinderRegistry
        implements ParameterBinderRegistry<HttpRequest>
        implements Freezable
        implements Stringable
    {
    construct()
        {
        binders = new Array();
        binders.add(new QueryParameterBinder());
        }

    construct (Array.Mutability mutability, Array<ParameterBinder<HttpRequest>> binders)
        {
        this.binders = new Array(mutability, binders);
        }
    finally
        {
        if (mutability == Constant)
            {
            makeImmutable();
            }
        }

    private Array<ParameterBinder<HttpRequest>> binders;

    @Override
    immutable RequestBinderRegistry freeze(Boolean inPlace = False)
        {
        if (&this.isImmutable)
            {
            return this.as(immutable RequestBinderRegistry);
            }

        if (binders.mutability == Constant)
            {
            // the underlying binders is already frozen
            assert &binders.isImmutable;
            return this.makeImmutable();
            }

        if (!inPlace)
            {
             return new RequestBinderRegistry(Constant, binders).as(immutable RequestBinderRegistry);
            }

        this.binders = binders.freeze(inPlace);
        return makeImmutable();
        }

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
     * @param req    the HTTP request to obtain parameters values from
     *
     * @return a RouteMatch bound to the arguments from the source request
     */
    RouteMatch bind(function void () fn, RouteMatch route, HttpRequest req)
        {
        Map<String, Object> arguments = new HashMap();
        for (Parameter p : route.requiredParameters(fn))
            {
            if (String                       name   := p.hasName(),
                ParameterBinder<HttpRequest> binder := findParameterBinder(p, req))
                {
                Type paramType = p.ParamType;
                BindingResult<paramType.DataType> result =
                        binder.bind(p.as(Parameter<paramType.DataType>), req);
                if (result.bound)
                    {
                    arguments.put(name, result.value);
                    }
                }
            }
        return route.fulfill(fn, arguments);
        }

    // ----- Stringable methods ----------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 0;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        "RequestBinderRegistry(".appendTo(buf);
        binders.appendTo(buf);
        ")".appendTo(buf);
        return buf;
        }
    }