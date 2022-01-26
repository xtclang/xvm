module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    package collections import collections.xtclang.org;
    import collections.*;

    void run()
        {
        String s = "/"[0..0);
        console.println($"s={s}");  // used to throw OutOfBounds
        }

    void report(Object o)
        {
        console.println($"{&o.actualType}: {o}");
        }
    }

