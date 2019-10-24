const MultiFunction(Function[] functions)
    {
    @Op MultiFunction add(Function function_)
        {
        return new MultiFunction(functions + function_);
        }
    }