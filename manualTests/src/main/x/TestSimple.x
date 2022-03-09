module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run( )
        {
        }

    void test(String? error)
        {
        if (error != Null)
            {
            return;
            }

        Int i = 0;
        while (i++ < 2)
            {
            if (i == 0)
                {
                error = "hello";
                }
            else
                {
                error ?:= "Illegal"; // this used to fail to compile
                }
            }
        }
    }
