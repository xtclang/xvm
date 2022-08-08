module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test("hello");
        }

    void test(String | Map value)
        {
        switch (value.is(_))
            {
            case String:
                console.println(value[0..2)); // this used to assert the compiler
                break;

            case Map<String, String>:
                for ((String key, String val) : value)
                    {
                    console.println(value);
                    }
                break;
            }
        }
    }