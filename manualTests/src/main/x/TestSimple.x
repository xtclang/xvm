module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Int x = 0;
        ++x -= x++;  // used to assert in the compiler
        }
    }