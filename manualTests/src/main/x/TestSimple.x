module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    void run()
        {
        Svc s = new Svc();

        console.println($"{s.foo()}");
        }

    service Svc
        {
        Int[] foo()
            {
            Int[] ints = new Int[];
            ints += 1;
            return ints; // this throws "not immutable return"
            }
        }
    }
