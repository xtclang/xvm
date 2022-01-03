module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        HashMap<String, String> map = new HashMap();
        map.put("hello", "world");

        HashMap<HashMap<String, String>, String> map2 = new HashMap();
        map2.put(map, "test");
        console.println(map2);
        }

    interface Test
        {
        @Override
        static <CompileType extends Test> Int hashCode(CompileType value)
            {
            return &this.actualClass.name.hashCode(); // this used to compile and fail at run-time
            }
        }
    }