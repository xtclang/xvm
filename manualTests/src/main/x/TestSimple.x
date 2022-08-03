module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test(); // this used to assert at run-time
        console.println(t.value2);
        }

    class Test
        {
        @Stub Int value2;
        }

    mixin Stub<Referent> into Var<Referent>
        {
        }
    }