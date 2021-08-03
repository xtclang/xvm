module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<String, Int> map = new HashMap();

        assert Int i := test(map, "a");
        console.println($"{i}");
        }

    conditional Int test(Map<String, Int> map, String key)
        {
        assert:debug;

        return True, computeIfAbsent(map, "a");
        }

    (Int, Boolean) computeIfAbsent(Map<String, Int> map, String key)
        {
        (Int value, Boolean wasAbsent) = map.computeIfAbsent(key,  () -> Int:42);
        return value, wasAbsent;
        }
    }
