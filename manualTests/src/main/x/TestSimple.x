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

        // TODO GG test("first()", set.first());
        // TODO GG Tuple<Boolean, Int> result = set.first();
        // TODO GG Tuple result = set.first();

        test("first()", set.&first   .invoke(Tuple:()));
        test("last()" , set.&last    .invoke(Tuple:()));
        // TODO GG test("next(2)"   , set.&next(2) .invoke(Tuple:()));
        test("next(2)"   , set.&next    .invoke(Tuple:(Int:2)));
        test("prev(2)"   , set.&prev    .invoke(Tuple:(Int:2)));
        test("ceiling(2)", set.&ceiling .invoke(Tuple:(Int:2)));
        test("floor(2)"  , set.&floor   .invoke(Tuple:(Int:2)));
        test("next(3)"   , set.&next    .invoke(Tuple:(Int:3)));
        test("prev(3)"   , set.&prev    .invoke(Tuple:(Int:3)));
        test("ceiling(3)", set.&ceiling .invoke(Tuple:(Int:3)));
        test("floor(3)"  , set.&floor   .invoke(Tuple:(Int:3)));
        test("next(4)"   , set.&next    .invoke(Tuple:(Int:4)));
        test("prev(4)"   , set.&prev    .invoke(Tuple:(Int:4)));
        test("ceiling(4)", set.&ceiling .invoke(Tuple:(Int:4)));
        test("floor(4)"  , set.&floor   .invoke(Tuple:(Int:4)));
        }

    // TODO GG void test(String desc, Tuple<Boolean, Int> result)
    void test(String desc, Tuple<> result)
        {
        console.println($"{desc}={result[0]}{result[0].as(Boolean) ? ", "+result[1] : ""}");
        }
    }
