module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Custom Test t = new Test(); // that used to blow up at run-time
        t.report(0);
        }

    service Test
        {
        void report(Int i)
            {
            console.print(i);
            }
        }

    mixin Custom<Referent>
            into Var<Referent>
        {
        }
    }