module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Map<Int, String> map1 = [1="a", 2="b"];
        Map<Int, String> map2 = [2="x", 3="y"];

        map1 = map1.putAll(map2);
        console.println(map1);
        }
    }