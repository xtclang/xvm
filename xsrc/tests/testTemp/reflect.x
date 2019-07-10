module TestReflection.xqiz.it
    {
    @Inject Ecstasy.io.Console console;

    void run()
        {
        testInstanceOf();
        }

    void testInstanceOf()
        {
        import Ecstasy.collections.HashMap;

        console.println("\n** testInstanceOf");

        Object o = new HashMap<Int, String>();
        assert &o.instanceOf(Map<Int, String>);
        assert !&o.instanceOf(Map<String, String>);
        }
    }