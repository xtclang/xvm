import ecstasy.reflect.Parameter;

/**
 * A `ParameterResolver` is able to resolve values for parameters to be used to call methods and functions.
 */
interface ParameterResolver {
    /**
     * Resolve the specified parameter.
     *
     * @param context  the `ExecutionContext` to use to resolve the `Parameter`
     * @param param    the `Parameter` to resolve
     *
     * @return True iff the `Parameter` was resolved
     * @return the resolved parameter value
     */
    <ParamType> conditional ParamType resolve(ExecutionContext context, Parameter<ParamType> param);

    /**
     * Resolve all the specified `Parameter`s.
     *
     * @param context  the `ExecutionContext` to use to resolve the `Parameter`
     * @param param    the `Parameter` to resolve
     *
     * @return a `Map` of `Parameter` to the resolved value for that `Parameter`
     *
     * @throws IllegalState if one or more parameters cannot be resolved
     */
    static Map<Parameter, Object> ensureResolved(ExecutionContext context, Parameter[] params) {
        (Map<Parameter, Object> values, Parameter[] unresolved) = resolveInternal(context, params);
        if (unresolved.empty) {
            return values;
        }

        ParameterResolver[] resolvers = context.registry.getResources(ParameterResolver);
        throw new IllegalState($"Failed to resolve parameters: unresolved={unresolved} resolvers={resolvers}");
    }

    /**
     * Resolve the specified parameters.
     *
     * @param context  the `ExecutionContext` to use to resolve the `Parameter`
     * @param params   the `Parameter`s to resolve
     *
     * @return True iff the parameters were resolved
     * @return a `Map` of `Parameter` to the resolved value for that `Parameter`
     */
    static conditional Map<Parameter, Object> resolve(ExecutionContext context, Parameter[] params) {
        (Map<Parameter, Object> values, Parameter[] unresolved) = resolveInternal(context, params);
        if (unresolved.empty) {
            return True, values;
        }
        return False;
    }

    private static (Map<Parameter, Object>, Parameter[]) resolveInternal(ExecutionContext context, Parameter[] params) {
        if (params.size == 0) {
            return Map:[], [];
        }

        Map<Parameter, Object> paramValues = new ListMap();
        Parameter[]            unresolved  = new Array();

        for (var param : params) {
            if (var value := ParameterResolver.resolveParameter(context, param, param.ParamType)) {
                paramValues.put(param, value);
            }
            else if (var defaultValue := param.defaultValue()) {
                paramValues.put(param, defaultValue);
            }
            else {
                unresolved.add(param);
            }
        }

        return paramValues, unresolved;
    }

    private static <ParamType> conditional Object resolveParameter(ExecutionContext context, Parameter<ParamType> param, Type<ParamType> type) {
        ParameterResolver[] resolvers = context.registry.getResources(ParameterResolver);
        for (ParameterResolver resolver : resolvers) {
            if (var value := resolver.resolve(context, param)) {
                return True, value;
            }
        }
        return False;
    }
}