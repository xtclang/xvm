module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<String, Int> map = new HashMap();

        assert Int i := test(map, "a");
        console.println($"{i}");

        try
            {
            testThrow(i, "oops");
            }
        catch (Exception e)
            {
            console.println(e.text);
            }
        console.println("ok");
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

    void testThrow(Int i, String text)
        {
        throw new Exception(text);
        }
    }
