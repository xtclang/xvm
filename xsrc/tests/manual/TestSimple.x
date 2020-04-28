module TestSimple
    {
    @Inject Console console;

    void run()
        {
        import ecstasy.collections.HashMap;

        Map<String, Int> m = new HashMap();

        console.println(m);
        console.println(new Test());
        console.println("done");
        }

    class Test
        {
        }
    }