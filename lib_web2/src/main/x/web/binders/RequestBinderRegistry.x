import ecstasy.reflect.Parameter;

/**
 * A ParameterBinderRegistry containing ParameterBinder instances that
 * bind parameters to values from a from a Request.
 */
class RequestBinderRegistry
        implements ParameterBinderRegistry<Request>
        implements Freezable
        implements Stringable
    {
    construct()
        {
        binders = new Array();
        binders.add(new QueryParameterBinder());
        }

    construct (Array.Mutability mutability, Array<ParameterBinder<Request>> binders)
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

    private Array<ParameterBinder<Request>> binders;

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
    void addParameterBinder(ParameterBinder<Request> binder)
        {
        binders.add(binder);
        }

    @Override
    conditional ParameterBinder<Request>
            findParameterBinder(Parameter parameter, Request source)
        {
        ParameterBinder<Request>? binder = Null;
        for (ParameterBinder<Request> pb : binders)
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
//    RouteMatch bind(function void () fn, RouteMatch route, Request req)
//        {
//        Map<String, Object> arguments = new HashMap();
//        for (Parameter p : route.requiredParameters(fn))
//            {
//            if (String                       name   := p.hasName(),
//                ParameterBinder<Request> binder := findParameterBinder(p, req))
//                {
//                BindingResult result = binder.bind(p, req);
//                if (result.bound)
//                    {
//                    arguments.put(name, result.value);
//                    }
//                }
//            }
//        return route.fulfill(fn, arguments);
//        }

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
