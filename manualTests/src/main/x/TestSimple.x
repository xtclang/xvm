module TestSimple
    {
    @Inject Console console;

    package oodb import oodb.xtclang.org;

    void run()
        {
         // both lines used to fail at run time
        console.print(oodb);
        console.print(oodb.isInvalidName("hello"));
        }
    }