module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        TestSvc svc = new TestSvc();

        Map<Int, String> m = new HashMap();

        svc.map = m;

        svc.foo();

        console.println(m);
        }

    service TestSvc
        {
        construct()
            {
            map = new HashMap();
            }

        void foo()
            {
            map.put(1, "hello");
            }

        Map<Int, String> map;
        }
    }