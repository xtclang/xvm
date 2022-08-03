module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        }

    class Test
        {
        @Stub Int value2;

        mixin Stub<Referent> into Var<Referent> // this used to cause an infinite recursion
            {
            }
        }
    }