module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        Map<Int, String> map = Map:[0="zero", 1="one", 2="two"];
        Test t = new Test(map);

        console.println(t.count);
        }

    class Test<K, V> (Map<K, V> map)
        {
        Int count = map.size; // compilation error
        }
    }
