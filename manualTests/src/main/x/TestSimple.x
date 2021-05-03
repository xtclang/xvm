module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test();
        console.println(t.value);

        t.&value.set(new HashMap());
        console.println(t.value);
        }

    class Test
        {
        @Lazy Map<Int, String> value.calc()
            {
            HashMap<Int, String> map = new HashMap();
            map.put(1, "a");
            return map;
            }
        }
    }