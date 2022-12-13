module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        new Test().test1();
        Test.test2();
        }

    class Test
        {
        Int external = 42;

        void test1()
            {
            new Inner1(1).report();

            class Inner1(Int value)
                {
                void report()
                    {
                    console.println(value);
                    console.println(external); // used to fail to compile
                    }
                }
            }

        static void test2()
            {
            new Inner2(2).report();

            class Inner2(Int value)
                {
                void report()
                    {
                    console.println(value);
//                    console.println(external); // compiler error
                    }
                }
            }
        }
    }