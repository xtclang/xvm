/**
 * A simple implementation of an ExecutableFunction that executes a method bound to a target.
 */
const SimpleExecutableFunction
        implements ExecutableFunction
    {
    /**
     * Create a SimpleExecutableFunction.
     *
     * @param method  the method to bind to the target
     * @param target  the target to bind the method to when creating the function to execute
     */
    construct(Method<Object, Tuple, Tuple> method, Function<<>, <Object>> constructor)
        {
        this.method            = method;
        this.constructor       = constructor;
        this.conditionalResult = method.conditionalResult;
        }

    @Override
    function void () createFunction()
        {
        Tuple         params = Tuple:();
        Tuple<Object> target = constructor.invoke(params);
        assert target.size == 1;
        return method.bindTarget(target[0]);
        }

    public/private Method<Object, Tuple, Tuple> method;

    @Override
    public/private Boolean conditionalResult;

    private Function<<>, <Object>> constructor;
    }
