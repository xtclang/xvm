module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<String, Int> map = new HashMap();

        (Int i, Boolean absent) = computeIfAbsent1(map, "a");
        console.println($"{i} {absent}");

        (i, absent) = computeIfAbsent2(map, "a");
        console.println($"{i} {absent}");
        }

    (Int, Boolean) computeIfAbsent1(Map<String, Int> map, String key)
        {
        Tuple<Int, Boolean> t = map.computeIfAbsent(key, () -> Int:1);

        (Int value, Boolean wasAbsent) = t;
        return value, wasAbsent;
        }

    (Int, Boolean) computeIfAbsent2(Map<String, Int> map, String key)
        {
        (Int value, Boolean wasAbsent) = map.computeIfAbsent(key,  () -> Int:2);
        return value, wasAbsent;
        }
    }
