module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        new Test().test();
        }

    service Test
        {
        private @Lazy(() -> new ecstasy.numbers.PseudoRandom()) Random rnd; // this used to fail to compile

        void test()
            {
            console.println(rnd.int(5));
            }
        }
    }