module TestMaps.xqiz.it
    {
    import X.collections.ExtHashMap;

    @Inject X.io.Console console;

    void run()
        {
        testBasic();
        }

    void testBasic()
        {
        Map<Int, String> map = new ExtHashMap();
        map.put(1, "Hello from Map");
        map.put(2, "Goodbye from Map");

        if (String s : map.get(1))
            {
            console.println(s);
            }
        console.println(map);
        }
    }