module TestSimple.test.org
    {
    @Inject Console console;
    void run()
        {
        Int[] ints = new Array<Int>(Mutable, [Int:1, Int:2]);
        clear(ints);

        Object[] objs = ["a", Int:1];
        clear(objs);
        }

    void clear(Object[] array)
        {
        console.println($"{array} {&array.actualType}");
        array = array.clear();
        console.println($"{array} {&array.actualType}");
        }
    }