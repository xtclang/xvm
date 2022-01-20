const MultiMethod<Target>(String name, Callable[] callables)
    {
    typedef (Method<Target> | Function) as Callable;

    construct(String name, Callable[] callables)
        {
        // each callable must have the correct name and must be unique
        Each: for (Callable c : callables)
            {
            assert:arg c.name == name;
            assert:arg Each.first || !callables.lastIndexOf(c, Each.count-1);
            }

        this.name      = name;
        this.callables = callables;
        }

    /**
     * The array of methods represented by the MultiMethod.
     */
    @Lazy Method<Target>[] methods.calc()
        {
        Method<Target>[] methods = new Method<Target>[];
        for (Callable callable : callables)
            {
            if (callable.is(Method<Target>))
                {
                methods.add(callable);
                }
            }
        assert methods.is(Freezable);
        return methods.freeze(True);
        }

    /**
     * The array of functions represented by the MultiMethod.
     */
    @Lazy Function[] functions.calc()
        {
        Function[] functions = new Function[];
        for (Callable callable : callables)
            {
            if (callable.is(Function))
                {
                functions.add(callable);
                }
            }
        assert functions.is(Freezable);
        return functions.freeze(True);
        }

    /**
     * Create a new MultiMethod by adding a callable to this MultiMethod.
     *
     * @param callable  the [Callable] method or function to add
     *
     * @return the new MultiMethod
     */
    @Op("+")
    MultiMethod!<Target> add(Callable callable)
        {
        assert:arg callable.name == name;
        assert:arg !callables.contains(callable);

        return new MultiMethod<Target>(name, callables + callable);
        }
    }