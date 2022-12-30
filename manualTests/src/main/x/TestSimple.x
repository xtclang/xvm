module TestSimple
    {
    @Inject Console console;

    void run()
        {
        immutable Int32[] ints = [1, 2, 3];
        for (Int i : ints)            // used to fail to compile
            {
            console.println(i);
            }

        for (Int i : ints.iterator())  // used to fail to compile
            {
            console.println(i);
            }

        Map<Int8, Int16> map = Map:[1=11, 2=12];
        for ((Int k, Int v) : map)     // used to fail to compile
            {
            console.println($"k={k}; v={v}");
            }

        for (Int k : map)               // used to fail to compile
            {
            console.println($"k={k}");
            }
        }
    }