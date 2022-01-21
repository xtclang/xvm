module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    package collections import collections.xtclang.org;
    import collections.*;

    void run()
        {
        Int i = 11;

        Set<Int> s1 = new ArrayOrderedSet([3,7,11]); // this used to fail to compile
        report(s1);

        Set<Int> s1a = new ArrayOrderedSet([3,7,i]); // this used to fail to compile
        report(s1a);

        Set<Int> s2 = [3,7,11];
        report(s2);

        Set<Int> s2a = [3,7,i];
        report(s2a);

        Collection<Int> a1 = Set:[3,7,i]; // this is questionable, but deferred for now
        report(a1);
        }

    void report(Object o)
        {
        console.println($"{&o.actualType}: {o}");
        }
    }
