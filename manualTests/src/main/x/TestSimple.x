module TestSimple.test.org
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    void run()
        {
        import ecstasy.collections.CaseInsensitiveHasher;
        import ecstasy.collections.HasherMap;

        Map<String, String> map = new HasherMap(new CaseInsensitiveHasher());
        map.put("hello", "world");
        map.put("Hello", "World");
        map.put("hELLO", "wORLD");
        console.println($"map={map}");
        }
    }