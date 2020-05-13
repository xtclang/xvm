module TestSimple
    {
    @Inject Console console;

    void run(  )
        {
        Map<String, Int> m0 = Map:["i"=1, "j"=2];
        console.println(m0);

        Int i = 1;
        Int j = 2;
        Map<String, Int> m = Map:["i"=i, "i"=j];
        console.println(m);
        }
    }