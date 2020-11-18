module TestSimple
    {
    @Inject Console console;

    void run()
        {
        enum Color {Red, Green, Blue}

        void test(Class clz)
            {
            console.println($"class name={clz.name}");
            if (clz.is(Enumeration))
                {
                console.println($"enumeration={clz.values[0].enumeration}");
                }
            else
                {
                assert clz.is(EnumValue);

                console.println($"enumValue={clz.value}; enumeration={clz.enumeration}");
                }
            }

        test(Color.Red);
        test(Color);
        }
    }
