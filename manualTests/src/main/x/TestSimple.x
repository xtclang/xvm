module CallMethod
    {
    const Example(String text)
        {
        @Override
        String toString()
            {
            return $"This is an example with text={text}";
            }
        }

    void run()
        {
        @Inject Console console;

        Example example = new Example("hello!");
        Method<Example, <>, <String>> method = Example.toString;
        function String() func = method.bindTarget(example);
        val func4 = &func();                                // used to assert the compiler
        console.println($"Calling a fully bound function: {func4()}");
        }
    }