module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        TestService s = new TestService();
        console.println($"foo()={s.foo^()}");
        }

    service TestService
        {
        Int foo()
            {
            return 42;
            }
        }
    }
