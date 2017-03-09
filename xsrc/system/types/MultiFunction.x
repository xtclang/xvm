const MultiFunction(Function[] functions)
    {
    @op MultiFunction add(Function function_)
        {
        return new MultiFunction(functions + function_);
        }
    }