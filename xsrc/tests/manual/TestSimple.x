module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestService svc = new TestService();
        @Future function Int(Int) f = svc.getFn();

        Int j = f(5);
        }

    service TestService
        {
        function Int(Int) getFn()
            {
            for (Int x : [0..1000])
                {
                }

            return i -> i+1;
            }
        }
    }