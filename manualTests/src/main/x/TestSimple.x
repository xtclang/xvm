module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    void run()
        {
        Int[] array = [1, 2, 3];

        Map<Int[], String> map = new HashMap(); // this used to fail at run-time
        map.put(array, "hello");

        console.println(map);
        }
    }
