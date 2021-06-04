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

        set.add(1277);
        set.add(12777);
        set.add(127777);
        set.add(127778);
        set.add(127779);
        set.add(227779);
        set.add(1277789999);
        set.add(1277799999);
        set.add(2277799999);
        set.add(22777999991);
        set.add(227779999912);
        set.add(2277799999123);
        set.add(22777999991234);
        set.add(227779999912345);
        console.println(set);

        set.remove(5);
        set.remove(1277);
        console.println(set);
        }
    }