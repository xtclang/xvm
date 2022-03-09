module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        }

    void test()
        {
        Int i = 0;
        while (True)
            {
            if (i >= j) // this line used to cause an assertion during code analysis
                {
                break;
                }
            }
        console.println(i);
        }

    Int j.get()
        {
        return 0;
        }
    }
