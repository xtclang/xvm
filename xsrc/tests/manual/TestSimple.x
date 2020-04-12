module TestSimple.xqiz.it
    {
    @Inject Console console;

    void run()
        {
        Boolean f = True;
        console.println(f);

        Class   c = True;
        console.println(c);

        Type    t = True;
        console.println(t);

        Class c1 = Map;
        console.println(c1);

        Class c2 = ecstasy.collections.ListMap;
        console.println(c2);

        Class c3 = Map<Int, String>;
        console.println(c3);

        Class c4 = ecstasy.collections.ListMap<Date, Time>;
        console.println(c4);

        Class c7 = ecstasy.collections.ListMap<Date, Time>.Entries;
        console.println(c7);

        Map<Int, String> map = new ecstasy.collections.ListMap();
        console.println(map);
        }
    }