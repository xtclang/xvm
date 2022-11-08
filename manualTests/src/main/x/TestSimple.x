module TestSimple
    {
    @Inject Console console;
    void run( )
        {
        for ((Int n1, Int n2) : Array<Tuple<Int,Int>>:[(0,7), (1,5)]) // this used to fail to compile
            {
            console.println($"n1={n1} n2={n2}");
            }
        }
    }