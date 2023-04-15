module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new TestConst(new TestSvc()).test();
        new TestConst2(new TestSvc()).test();
        }

    interface TestIface
        {
        Int value();
        }

    service TestSvc
            implements TestIface
        {
        @Override
        Int value()
            {
            return 42;
            }
        }

    const TestConst(TestIface svc)
        {
        void test()
            {
            assert svc.value() == 42; // this used to assert at run-time
            }
        }

    const TestConst2(TestIface? svc)
        {
        void test()
            {
            assert svc?.value() == 42;  // this used to assert at run-time
            }
        }
    }