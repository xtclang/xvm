module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        test(Null, 0);
        }

    void test(String? s, Int i)
        {
        if (s != Null)
            {
            return;
            }

        switch (i)
            {
            case 0:
                s = "zero";
                break;

            case -1:
            default:
                s = "error";
                continue;
            }

        s = s.trim(); // this used to fail to compile
        console.println(s);
        }
    }
