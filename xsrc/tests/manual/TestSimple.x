module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        Tuple<String, Char> t1 = ("big", '?');

        val t2 = Tuple:(1).add(t1);
        console.println(t2);
        }
    }
