module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Derived d = new Derived([1..5]);
        console.println(d);
        }

    @Abstract static const Base
        {
        @Abstract @RO Int start;
        @Abstract @RO Int end;
        }

    const Derived(Range<Int> range, Int end = 0) // compilation error
        {
        Int start.get()
            {
            return range.first;
            }

        Int end.get()
            {
            return range.last;
            }
        }
    }