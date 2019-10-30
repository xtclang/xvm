const MultiMethod<Target>(String name, Callable[] callables)
    {
    typedef (Method<Target> | Function) Callable;

    construct(String name, Callable[] callables)
        {
        // each callable must have the correct name and must be unique
        Each: for (Callable c : callables)
            {
            // TODO GG assert:arg c.name == name;
            assert:arg (c.is(Method) && c.name == name) || (c.is(Function) && c.name == name);
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
        return methods.ensureImmutable(True);
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
        return functions.ensureImmutable(True);
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
        // TODO GG assert:arg callable.name == name;
        assert:arg (callable.is(Method) && callable.name == name) || (callable.is(Function) && callable.name == name);
        assert:arg !callables.contains(callable);

        return new MultiMethod<Target>(name, callables + callable);
        }
    }