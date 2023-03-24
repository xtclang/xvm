module TestSimple
    {
    @Inject Console console;

    void run(   )
        {
        Int[] ints = [1, 2, 3];
        Int   size = ints.size;

        Loop1: for (Int i : 0 ..< size)
            {
            console.print($"{i=} {Loop1.first=} {Loop1.last=}");
            }

        size = 0;
        Loop2: for (Int i : 0 .. size)
            {
            console.print($"{i=} {Loop2.first=} {Loop2.last=}");
            }

        Loop3: for (Int i : size >.. 0)
            {
            TODO
            }
        }
    }