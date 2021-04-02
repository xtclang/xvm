module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<Int, String> map = new Test().calc();
        if (String s := map.get(1))
            {
            console.println(s);
            }
        }

    class Test()
        {
        Map<Int, String> calc()
            {
            return new Map()
                {
                @Override
                conditional Value get(Key key)
                    {
                    return True, "Hello";
                    }

                @Override
                @Lazy Set<Int> keys.calc()
                    {
                    TODO
                    }
                };
            }
        }
    }