module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Int[] ints = new Array(1, i ->
            {
            Int x = ""; // type mismatch used to not be reported
            return x;
            });

        report(() ->
            {
            Int x = y;  // unresolvable "y" used to not be reported
            return "hello";
            });
        }

    void test(function String () report)
        {
        }
    }
