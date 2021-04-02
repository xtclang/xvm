module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        import ecstasy.collections.SkiplistMap;

        Map<Int, String> map1 = new SkiplistMap();

        map1.put(1, "a");
        map1.put(2, "b");

        Map<Int, String> map2 = new SkiplistMap(2, (k1, k2) -> k2 <=> k1);

        map2.putAll(map1);

        console.println(map1 == map2);
        }
    }