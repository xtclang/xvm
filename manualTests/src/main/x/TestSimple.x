module TestSimple.test.org
    {
    package collections import collections.xtclang.org;

    @Inject Console console;

    void run()
        {
        import collections.SparseIntSet;

        Set<Int> set = new SparseIntSet();

        set.add(5);
        console.println($"set(5)={set} (size={set.size})");

        set.add(7);
        console.println($"set(5,7)={set} (size={set.size})");

        set.add(127);
        console.println($"set(5,7,127)={set} (size={set.size})");
        }
    }