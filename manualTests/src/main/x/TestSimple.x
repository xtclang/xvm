module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Type t1 = String?;
        Type t2 = t1 - Nullable;
        console.print(t2); // used to print "String? - Nullable"
        }
    }