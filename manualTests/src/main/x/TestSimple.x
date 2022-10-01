module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        Test t = new Test();
        assert t.n.equals(1); // used to blow up the compiler
        }

    class Test
        {
        Int n;
        }
    }