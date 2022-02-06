module TestSimple.test.org
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        testDuration(Duration:123456s);
        testDuration(Duration:P1DT12H30M0.02234S);
        }

    void testDuration(Duration d)
        {
        String iso = d.toString(True);
        console.println($"{d} -> {iso}");

        Duration d2 = new Duration(iso);
        assert d2 == d;
        }
    }

