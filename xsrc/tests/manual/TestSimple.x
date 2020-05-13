module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int i = 1;
        Int j = 2;
        Int[] ints = [i, 0, j];

        Tuple<String, Int> t = ("j", j);

        console.println(ints);
        console.println(t);
        }
    }