const MultiMethod(Method[] methods)
    {
    @op MultiMethod add(Method method)
        {
        return new MultiMethod(methods + method);
        }
    }