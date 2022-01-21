module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    package collections import collections.xtclang.org;
    import collections.*;

    void run()
        {
        Int i1 = 3;
        Int i2 = 7;

        Array<Int> a1 = [i1,i2,11];
        Array<Int> a2 = [2,7,12];

        Set<Int> s1 = [i1,i2,11];
        Set<Int> s2 = [2,7,12];

        Set<Int> s3 = s1.addAll(s2);
        report(s3);
        Set<Int> s4 = s3.addAll(s3);
        report(s4);

        s4.removeAll(s1); // used to throw
        console.println(s4);
        }

    void report(Object o)
        {
        console.println($"{&o.actualType}: {o}");
        }
    }
