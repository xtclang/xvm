module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    package collections import collections.xtclang.org;
    import collections.*;

    void run()
        {
        console.println(bar(""));
        console.println(bar(Null));
        }

    void report(Object o)
        {
        console.println($"{&o.actualType}: {o}");
        }

    Int bar(String? s)
        {
        return switch (s?)  // this used to fail to compile
            {
            case "Hello":   1;
            case "Goodbye": 2;
            default:        0;
            } : -1;
        }

    }

