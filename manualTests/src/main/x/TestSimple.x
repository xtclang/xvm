module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        new Test().testIs();
        }

    class Test
        {
        Boolean[] test = new Array(2);
        Int[]     prop = new Array(2);

        void testIs()
            {
            Object o = "";

            // *** test IsExpression
            if (Int i := o.is(Int))  // used to produce a "suspicious assignment" warning
                {
                console.println($"1) {i}");
                }

            if (prop[0] := o.is(Int))  // used to produce a "suspicious assignment" warning
                {
                console.println($"2) {prop[0]}");
                }

//            (test[0], prop[0]) = o.is(Int); // this used to compile
//            console.println(prop[0]);

            // *** test InvocationExpression
            if (Int i := isInt(o))
                {
                console.println($"3) {i}");
                }

            if (prop[1] := isInt(o)) // used to throw "IllegalState: Unassigned value: ""
                {
                console.println($"4) {prop[1]}");
                }

//            (test[1], prop[1]) = isInt(o); // this used to compile
//            console.println(prop[1]);
            }

        conditional Int isInt(Object o)
            {
            return o.is(Int);
            }
        }
    }
