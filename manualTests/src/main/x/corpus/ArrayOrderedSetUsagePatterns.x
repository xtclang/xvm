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

        test("first()"   , set.&first()    .invoke(Tuple:()));
        test("last()"    , set.&last       .invoke(Tuple:()));

        test("next(2)"   , set.&next(2)    .invoke(Tuple:()));
        test("prev(2)"   , set.&prev(2)    .invoke(Tuple:()));
        test("ceiling(2)", set.&ceiling(2) .invoke(Tuple:()));
        test("floor(2)"  , set.&floor(2)   .invoke(Tuple:()));

        test("next(3)"   , set.&next(3)    .invoke(Tuple:()));
        test("prev(3)"   , set.&prev(3)    .invoke(Tuple:()));
        test("ceiling(3)", set.&ceiling(3) .invoke(Tuple:()));
        test("floor(3)"  , set.&floor(3)   .invoke(Tuple:()));

        test("next(4)"   , set.&next(4)    .invoke(Tuple:()));
        test("prev(4)"   , set.&prev(4)    .invoke(Tuple:()));
        test("ceiling(4)", set.&ceiling(4) .invoke(Tuple:()));
        test("floor(4)"  , set.&floor(4)   .invoke(Tuple:()));
        }

    // TODO GG void test(String desc, Tuple<Boolean, Int> result)
    void test(String desc, Tuple<> result)
        {
        console.println($"{desc}={result[0]}{result[0].as(Boolean) ? ", "+result[1] : ""}");
        }
    }
