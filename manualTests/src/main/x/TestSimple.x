module TestSimple.test.org
    {
    package collections import collections.xtclang.org;
    import collections.ArrayOrderedSet;

    @Inject Console console;

    void run()
        {
        Int[] nums = [3,7,11];
        ArrayOrderedSet<Int> set = new ArrayOrderedSet<Int>(nums);
        // TODO GG ArrayOrderedSet<Int> set = new ArrayOrderedSet<Int>([3,7,11]);

        // all three scenarios below used to fail to compile
        test("first()", set.first());

        Tuple r0 = set.first();
        console.println(r0);

        Tuple<Boolean, Int> r2 = set.first();
        console.println(r2);
        }

    void test(String desc, Tuple<Boolean, Int> result)
        {
        console.println($"{desc}={result[0]}{result[0] ? ", "+result[1] : ""}");
        }
    }
