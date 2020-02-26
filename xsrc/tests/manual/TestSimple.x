module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        console.println("hello");

        Type t1 = Int;
        Type t2 = String;

        Map<Type, String> map = new HashMap();
        map.put(t1, "Int");
        map.put(t2, "String");

        console.println(map.getOrNull(t1));
        console.println(map.getOrNull(t2));

        console.println(Type.hashCode(t1));
        }
    }
