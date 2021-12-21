module TestSimple.test.org
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import collections.ArrayOrderedSet;

    void run()
        {
        new Test().test();
        }

    class Test
        {
        void test()
            {
            Map<Int, Int> byReadId = new SkiplistMap();
            byReadId.put(1, 1);
            byReadId.put(2, 1);

            Set<Int> cleanupRetained = byReadId.keys;

            Set<Int> lastCleanupRetained = new ArrayOrderedSet<Int>(
                    cleanupRetained.toArray(Constant)).freeze(inPlace=True);

            // the comparison below used to blow up with
            // *** Exception occurred during background maintenance: Exception: Missing method "Boolean containsAll(Collection<Object>)" on this:SkiplistMap<Int, Int>.KeySet
            //   at collections.Collection.equals(Type<this:class(Collection)>, equals(?)#CompileType, equals(?)#CompileType) (Collection.x:959)
            console.println(cleanupRetained == lastCleanupRetained);
            }
        }
    }