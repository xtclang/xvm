module TestMaps.xqiz.it
    {
    import X.collections.ExtHashMap;
    import X.collections.ListMap;

    import X.Duration;

    @Inject X.io.Console console;
    @Inject X.Timer      timer;

    void run()
        {
        testBasic();

        function void () run = &testFill100();
        profile(run, 10);

        testListMap();
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

    static void testFill100()
        {
        Map<Int, Int> map = new ExtHashMap();
        for (Int i = 0; i < 100; i++)
            {
            map.put(i, i);
            }
        }

    void profile(function void () run, Int iterations)
        {
        timer.reset();
        for (Int i = 0; i < iterations; i++)
            {
            run();
            }
        Duration time = timer.elapsed;
        console.println("Elapsed " + time.millisecondsTotal +" ms");
        console.println("Latency " + (time / iterations).milliseconds + " ms");
        }

    void testListMap()
        {
        Map<String, String> map = new ListMap();
        console.println("empty map=" + map);

        map.put("hello", "world");
        console.println("map=" + map);
        }
    }