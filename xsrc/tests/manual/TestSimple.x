module TestSimple.xqiz.it
    {
    import ecstasy.web.json.IntNumberMapping;

    @Inject ecstasy.io.Console console;

    void run()
        {
        Test<Int> t = new Test(5);
        t.iterator();


//        Map<Type, function IntNumber(IntLiteral)> map = IntNumberMapping.CONVERSION;
//        if (function IntNumber(IntLiteral) f := map.get(Int))
//            {
//            console.println(f(17));
//            }

        // map[type](17); TODO GG: doesn't compile
        String? s = NAMES.getOrNull(1);
        function Int(Int)? fn = FUNCTIONS.getOrNull(1);

        console.println(s);
        console.println(fn?(2));
        }

    static Map<Int, String> NAMES = Map:[1="one", 2="two"];
    static Map<Int, function Int(Int)> FUNCTIONS =
        Map:[1 = (n) -> n + 1, 2 = (n) -> n + 1];

    static Int foo(Int n)
        {
        return 5;
        }

    class Test<Element>(Element lo)
        {
        function Int(Int) fn = n -> n + 1;
        Element lower;

        Iterator iterator()
            {
            Element lo = lower;

            console.println(fn);

            return new Iterator()
                {
                private Element lastValue = lo;

                @Override
                conditional Element next()
                    {
                    return false;
                    }
                };
            }
        }
    }
