module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String[] words = ["hello", "world"];
        console.print(words.hashCode());

        test(words);
        }

    static <CompileType extends Array> void test(CompileType collection)
        {
        typedef CompileType.Element? as NType;

        Iterator<CompileType.Element> iter = collection.iterator();
        NType[] nts = new NType[collection.size](_ -> iter.take()); // this used to fail at run-time
        assert !iter.next();
        }
    }