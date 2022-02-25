module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    class Base(Int base)
        {
        }

    class Derived1(String s, Int prop="hello")  // <-- this used to compile
        {
        construct(String s, Int i)
            {
            }
        }

    class Derived2(String s, Int prop=6)
            extends Base(what)                  // <-- this used to compile
        {
        construct(String s, Int i)
            {
            }
        }
    }
