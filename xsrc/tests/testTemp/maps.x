module TestMaps.xqiz.it
    {
    import X.collections.HashMap;
    import X.collections.ListMap;

    import X.Duration;

    @Inject X.io.Console console;
    @Inject X.Timer      timer;

    void run()
        {
        testBasic();
        testProfile();

        testListMap();
        }

    void testBasic()
        {
        console.println("\n** testBasic()");

        Map<Int, String> map = new HashMap();
        map.put(1, "Hello from Map");
        map.put(2, "Goodbye from Map");

        if (String s : map.get(1))
            {
            console.println(s);
            }
        console.println(map);
        }

    void testProfile()
        {
        console.println("\n** testProfile()");
        function void () run = &testFill100();
        profile(run, 10);
        }

    static void testFill100()
        {
        Map<Int, Int> map = new HashMap();
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
        console.println("\n** testListMap()");

        Map<String, String> map = new ListMap();
        console.println("empty map=" + map);

        console.println("adding entries...");
        map.put("k1", "v2");
        map.put("k2", "v2");

        // TODO GG map.empty fails, map.keys.empty fails, etc.: console.println("map.size=" + map.size + ", map.empty=" + map.empty);
        console.println("map.size=" + map.size);
        console.println("keys.size=" + map.keys.size);
        console.println("entries.size=" + map.entries.size);
        console.println("values.size=" + map.values.size);
        console.println("map=" + map);

        console.println("keys:");
        loop: for (String key : map.keys)
            {
            console.println("[" + loop.count + "]=" + key);
            }
        // TODO CP "loop" should cease to exist in the context at this point

        console.println("values:");
        loop2: for (String value : map.values)
            {
            console.println("[" + loop2.count + "]=" + value);
            }

        console.println("entries:");
        loop3: for (Map<String,String>.Entry entry : map.entries)
            {
            console.println("[" + loop3.count + "]=" + entry.key + "=" + entry.value);
            }
        }
    }