module TestMaps
    {
    import ecstasy.collections.maps.KeyEntries;

    @Inject Console console;
    @Inject Timer   timer;

    void run()
        {
        testBasic();
        testEquals();

        function void () run = &testFill100();
        profile(run, 10);

        testListMap();

        testMapIteration();
        }

    void testBasic()
        {
        console.println("\n** testBasic()");

        Map<Int, String> map = new HashMap();
        map.put(1, "Hello from Map");
        map.put(2, "Goodbye from Map");

        if (String s := map.get(1))
            {
            console.println(s);
            }
        console.println(map);
        }

    void testEquals()
        {
        Map<Int, String> map1 = new HashMap();
        map1.put(1, "v1");

        Map<Int, String> map2 = Map:[1="v1"];

        assert map1 == map2;
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
        console.println($"Elapsed {time.milliseconds} ms");
        console.println($"Latency {(time / iterations).milliseconds} ms");
        }

    void testListMap()
        {
        console.println("\n** testListMap()");

        Map<String, String> map = new ListMap();
        console.println($"empty map={map}");

        console.println("adding entries...");
        map["k1"] = "v1";
        map["k2"] = "v2";

        console.println($"map.size={map.size}, map.empty={map.empty}");
        console.println($"keys.size={map.keys.size}");
        console.println($"entries.size={map.entries.size}");
        console.println($"values.size={map.values.size}");
        console.println($"map={map}");

        console.println("keys/values:");
        loop: for (String key : map.keys)
            {
            console.println($"[{loop.count}]:{key}={map[key]}");
            }

        // same thing, but using "$" syntax
        console.println($"keys:{{L: for (val v : map.keys) {$.add($"\n[{L.count}]={v}");} return;}}");

        console.println("values:");
        loop: for (String value : map.values)
            {
            console.println($"[{loop.count}]:{value}");
            }

        console.println("entries:");
        loop: for (Map<String,String>.Entry entry : map.entries)
            {
            console.println($"[{loop.count}]:{entry.key}={entry.value}");
            }

        console.println("key-entries:");
        loop: for (Map<String, String>.Entry entry : new KeyEntries<String, String>(map))
            {
            console.println($"[{loop.count}]:{entry}");
            }

        // test very bad hashing
        const Point(Int x)
            {
            static <CompileType extends Point> Int hashCode(CompileType value)
                {
                return 100 + value.x % 3;
                }
            }

        ListMap<Point, Int> map2 = new ListMap();
        for (Int i : [0..12))
            {
            map2.put(new Point(i), i);
            }

        for (Int i : [0..12))
            {
            assert Int v := map2.get(new Point(i));
            assert v == i;
            }

        for (Int i : [0..12))
            {
            map2.remove(new Point(i));
            map2.put(new Point(12 + i), 12 + i);
            }

        for (Int i : [12..24))
            {
            assert Int v := map2.get(new Point(i));
            assert v == i;
            }
        }

    void testMapIteration()
        {
        console.println("\n** testListMap()");

        Map<String, Int> map = new HashMap();
        map.put("hello", 1);
        map.put("goodbye", 2);

        console.println("keys:");
        for (String key : map)
            {
            console.println($"{key}");
            }

        console.println("keys and values:");
        for ((String s, Int i) : map)
            {
            console.println($"{s} = {i}");
            }

        console.println("values:");
        for ((_, Int i) : map)
            {
            console.println($"? = {i}");
            }
        }
    }