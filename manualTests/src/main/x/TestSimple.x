module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Map<Int, String> map = new HashMap();

        Type type = &map.actualType;
        console.println(type);

        Type type1 = (immutable).add(type);
        console.println(type1);

        Type type2 = type.add((immutable));
        console.println(type2);
        }
    }