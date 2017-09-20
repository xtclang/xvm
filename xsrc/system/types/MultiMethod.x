const MultiMethod(Method[] methods)
    {
    @Op MultiMethod add(Method method)
        {
        return new MultiMethod(methods + method);
        }
    }