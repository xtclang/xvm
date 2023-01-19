/**
* One of Dima's tests that overwhelms the suspended fiber queue.
*/
module TestOverwhelm
    {
    @Inject Console console;

    void run()
        {
        console.print("Starting test");

        Test one = new Test();
        Test two = new Test();

        one.test();
        two.test();

        console.print("done");
        }

    service Test
        {
        void test()
            {
            for (Int i = 0; i < 1000000; i++)
                {
                if (i % 1000 == 0)
                    {
                    console.print($"-->{i}");
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
                console.print($"{i}<--");
                }
            test.incremented(i);
            }
        }
    }