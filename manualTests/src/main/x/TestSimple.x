module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String[] words = ["hello", "world"];
        console.print($"{words.hashCode()=}");

        test(words);
        }

    static <CompileType extends Array> void test(CompileType collection)
        {
        typedef CompileType.Element? as NType;

        NType[] nts = new Array<NType>(Fixed, collection);

        console.print($"before: {nts==collection.as(NType[])=}");

        nts[0] = Null;

        console.print($"after: {nts==collection.as(NType[])=}");

        if (CompileType.Element.is(Type<Hashable>))
            {
            Int64 hash = nts.hashCode(); // this used to blow up at run-time
            console.print(hash);
            }
        }
    }