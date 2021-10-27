module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        TestService svc = new TestService();
        svc.test();
        console.println(svc.serviceControl.statusIndicator);
        }

    service TestService
        {
        void test()
            {
            ServiceStatus indicator = serviceControl.statusIndicator;
            console.println(indicator);
            }
        }
    }