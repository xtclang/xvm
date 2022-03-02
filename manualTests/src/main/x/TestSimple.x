module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Test tOK = new Test(new Reporter().makeImmutable());
        tOK.test();

        Test tErr = new Test(new Reporter()); // used to be allowed - passing mutable state to a service!!
        tErr.test();
        }

    class Reporter()
        {
        void report()
            {
            console.println(this);
            }
        }

    service Test(Reporter reporter)
        {
        void test()
            {
            reporter.report();
            }
        }
    }
