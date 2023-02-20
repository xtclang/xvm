module TestSimple
    {
    @Inject Console console;

    void run()
        {
        List<String> l1 = ["a", "b"];
        Type<Stringable> type = l1.Element;

        List<type> l2 = new Array();      // this used to produce "unresolved name" error
        // List<typeOfInt> l3 = new Array(); // ditto; now: "A simple formal type is expected"

        console.print(&l2.actualType);
        }

    Type typeOfInt = Int;
    }