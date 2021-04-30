module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        OrderedMap<Int, String> map1 = new SkiplistMap();

        map1.put(1, "a");
        map1.put(2, "b");
        map1.put(3, "c");
        test(map1, Lesser);

        OrderedMap<Int, String> map2 = map1[3..1];
        console.println(map2);

        // test(map2, Greater);
        // TODO some test with an entry from map1 and an entry from map2 should blow up
        }

    void test(OrderedMap<Int, String> map, Ordered result)
        {
        @Unassigned OrderedMap<Int, String>.Entry prev;
        Loop: for (val entry : map.entries)
            {
            if (!Loop.first)
                {
                console.println($"entry={entry}; prev={prev}; comp={prev<=>entry}");
                }

            prev = entry.reify();
            }
        }
   }