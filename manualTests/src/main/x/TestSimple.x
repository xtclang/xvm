module TestSimple.test.org
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import collections.CircularArray;

    void run()
        {
        var a = gen();
        console.println($"0..10={a}");

        a = gen().deleteAll([0..2]);
        console.println($"0..10..deleteAll([0..2])={a}");

        a = gen().deleteAll([9..10]);
        console.println($"0..10..deleteAll([9..10])={a}");

        a = gen().deleteAll([3..4]);
        console.println($"0..10..deleteAll([3..4])={a}");

        a = gen().deleteAll([3..4].as(Range<Int>).reversed());
        console.println($"0..10..deleteAll([3..4].reversed())={a}");

        a = gen().deleteAll([3..3]);
        console.println($"0..10..deleteAll([3..3])={a}");

        a = gen().deleteAll([3..3));
        console.println($"0..10..deleteAll([3..3))={a}");

        a = gen().deleteAll([3..3).as(Range<Int>).reversed());
        console.println($"0..10..deleteAll([3..3).reversed())={a}");

        a = gen().deleteAll([7..8]);
        console.println($"0..10..deleteAll([7..8])={a}");

        a = gen().deleteAll([7..8].as(Range<Int>).reversed());
        console.println($"0..10..deleteAll([7..8].reversed())={a}");
        }

    CircularArray<Int> gen()
        {
        CircularArray<Int> a = new CircularArray();
        for (Int i : 0..10)
            {
            a += i;
            }
        return a;
        }
    }