import xunit.ParameterResolver;

/**
 * A `MethodExecutor` can invoke methods and functions, using supplied `ParameterResolver`s
 * to bind any invocation parameters.
 */
const MethodExecutor {
    /**
     * Invoke a `Function` using any registered `ParameterResolver` resources
     * to resolve parameters for the function.
     *
     * @param fn         the function to invoke
     * @param resolvers  the `ParameterResolver`s to use to resolve any function parameters
     *
     * @return the result of invoking the function
     */
    Tuple invoke(Function<Tuple, Tuple> fn, EngineExecutionContext context) {
        if (fn.params.size == 0) {
            return fn();
        }
        Map<Parameter, Object> params   = ParameterResolver.ensureResolved(context, fn.params);
        Function               fnInvoke = fn.bind(params);
        return fnInvoke();
    }

    /**
     * Invoke a `Method` using any registered `ParameterResolver` resources
     * to resolve parameters for the function.
     *
     * @param method     the `Method` to invoke
     * @param target     the target instance to invoke the method on
     * @param resolvers  the `ParameterResolver`s to use to resolve any method or
     *                   function parameters
     *
     * @return the result of invoking the function
     */
    Tuple invoke(Method method, Object target, EngineExecutionContext context) {
        Function<Tuple, Tuple> fn = method.bindTarget(target);
        return invoke(fn, context);
    }

    /**
     * Invoke a `Method` using any registered `ParameterResolver` resources to
     * resolve parameters for the method and return the single result returned
     * by the invocation.
     *
     * @param method     the `Method` to invoke
     * @param target     the target instance to invoke the method on
     * @param resolvers  the `ParameterResolver`s to use to resolve any method or
     *                   function parameters
     *
     * @return `True` iff the invocation returned a result
     * @return the single result of invoking the function
     */
    conditional Object invokeSingleResult(Method method, Object target, EngineExecutionContext context) {
        Tuple tuple = invoke(method, target, context);
        if (tuple.size > 0) {
            return True, tuple[0];
        }
        return False;
    }

    /**
     * Invoke a `Function` using any registered `ParameterResolver` resources to resolve
     * parameters for the function and return the single result returned by the invocation.
     *
     * @param fn         the `Function` to invoke
     * @param resolvers  the `ParameterResolver`s to use to resolve any method or
     *                   function parameters
     *
     * @return `True` iff the invocation returned a result
     * @return the single result of invoking the function
     */
    conditional Object invokeSingleResult(Function<Tuple, Tuple> fn, EngineExecutionContext context) {
        Tuple tuple = invoke(fn, context);
        if (tuple.size > 0) {
            return True, tuple[0];
        }
        return False;
    }
}
