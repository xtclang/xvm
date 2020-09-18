module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Set<String> set = ["a", "b", "c"];
        assert set.is(immutable Object);
        console.println(set.toString());
        }
    }
