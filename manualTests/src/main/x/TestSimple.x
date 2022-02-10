module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        console.println(new Point(0, 0));
        console.println(new SPoint(0, 0));
        console.println(new CPoint(0, 0));
        }

    class Point(Int x, Int y)
        {
        @Override
        String toString()
            {
            return $"({x}, {y})";
            }
        }

    service SPoint(Int x, Int y)
        {
        @Override
        String toString()
            {
            return $"S({x}, {y})";
            }
        }

    const CPoint(Int x, Int y)
        {
        }
    }
