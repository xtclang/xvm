/**
 * A simple implementation of an ExecutableFunction that executes a method bound to a target.
 */
class SimpleExecutableFunction
        implements ExecutableFunction
    {
    /**
     * Create a SimpleExecutableFunction.
     *
     * @param method  the method to bind to the target
     * @param target  the target to bind the method to when creating the function to execute
     */
    construct(Method<Object, Tuple, Tuple> method, Object target)
        {
        this.fn                = method.bindTarget(target);
        this.conditionalResult = method.conditionalResult;
        }

    @Override
    public/private function void () fn;

    @Override
    public/private Boolean conditionalResult;
    }
