module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        Test one = new Test();
        Test two = new Test();

        one.other = two;
        two.other = one;

        one.test();
        two.test();

        console.println("done");
        }

        service Test {

                Test? other;
                Int i = 0;

                void test() {
                        for (Int i = 0; i < 10000; i++) {
                                other?.increment(this);
                        }
                        console.println($"done - {i}");
                }

                void increment(Test test) {
                        i++;
                        //if (i % 1000 == 0) {
                        if (True) {
                                console.println($"{this} - {i}");
                        }
                        test.increment2();
                }

                void increment2() {
                        i++;
                        //if (i % 1000 == 0) {
                        if (True) {
                                console.println($"2 {this} - {i}");
                        }
                }
        }


//    void run()
//        {
//        import ecstasy.Timeout;
//
//        console.println();
//        Test t1 = new Test(1);
//        console.println(t1.serviceName);
//        t1.reentrancy = Open;
//        using (Timeout timeout = new Timeout(Duration:1M, true))
//            {
//            t1.runTest();
//            t1.shutdown();
//
//            console.println(t1.stop);
//            t1.runTest();
//            }
//        }
//
//    service Test(Int i)
//        {
//        Boolean stop;
//        void runTest()
//            {
//            console.println($"running service {i}");
//            }
//        }
    }
