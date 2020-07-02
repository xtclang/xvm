module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting Dima's test");

        Test one = new Test();
        Test two = new Test();

        one.test();
        two.test();

        console.println("done");
        }

    service Test
        {
        void test()
            {
            for (Int i = 0; i < 1000000; i++)
                {
                if (i % 1000 == 0)
                    {
                    console.println($"-->{i}");
                    }
                TestStatic.increment(this);
                }
            }

        void incremented(Int i)
            {
            }
        }

    static service TestStatic
        {
        Int i = 0;

        void increment(Test test)
            {
            i++;
            if (i % 1000 == 0)
                {
                console.println($"{i}<--");
                }
            test.incremented(i);
            }
        }
    }
