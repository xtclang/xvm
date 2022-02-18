module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Int   l  = 100;
        Int32 j  = l.toInt32();
        l      -= --j;          // this used to throw StackOverflow during compilation
        console.println(l);
        }
    }
