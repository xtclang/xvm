module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int[] ints = new Int[];
        ints.add(0).add(1);

        console.println(test(ints));
        }

    Boolean test(Int[] ints)
        {
        immutable Int[] myInts = [0, 1];
        return myInts == ints;
        }
    }
