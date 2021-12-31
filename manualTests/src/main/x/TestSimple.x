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

        map.put("hello this is a long string of characters that is at least 64 characters long! yay 12345!", "first");
        map.put("Hello this is a long string of characters that is at least 64 characters long! yay 12345!", "second");
        map.put("hello this is a LONG string of characters that is at least 64 characters long! yay 12345!", "third");
        console.println($"map={map}");
        }
    }