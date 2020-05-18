module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestService svc = new TestService();
        svc.i++;
        Int j = svc.&i.exchange(2);
        console.println(j);
        }

    service TestService
        {
        @Atomic Int i = 1;
        }
    }