module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String[] words1 = ["hello", "world"];
        String[] words2 = ["world", "hello"];

        Collection<Object> co1 = words1;
        Collection<Object> co2 = words2;
        assert co1 == co2; // this used to blow at run time

        Collection<String> cs1 = words1;
        Collection<String> cs2 = words2;
        assert cs1 == cs2;
        }
    }