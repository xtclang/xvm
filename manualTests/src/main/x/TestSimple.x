module TestSimple
    {
    @Inject Console console;

    void run()
        {
        import ecstasy.reflect.*;

        Type type = TestClass;
        for (Method m : type.methods)
            {
            if (m.is(Test))
                {
                console.println($"\"{m}\" is annotated with @Test({m.group})");
                }
            }
        for (Function f : type.constructors)
            {
            if (f.is(Test))
                {
                console.println($"constructor \"{f}\" is annotated with @Test({f.group})");
                }
            }
        for (Function f : type.functions)
            {
            if (f.is(Test))
                {
                console.println($"function \"{f}\" is annotated with @Test({f.group})");
                }
            }
        }

    @Test
    void methodInModule()
        {
        }

    class TestClass(String value)
        {
        @Test(Test.Unit)
        construct()
            {
            value = "Default";
            }

        @Test
        void testMethod()
            {
            }

        @Test(Test.Omit)
        void omittedMethod()
            {
            }

        @Test(Test.Slow)
        static void testFunction()
            {
            }
        }
    }