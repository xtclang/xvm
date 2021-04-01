module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<Int, String> m1 = new HashMap();
        m1.put(1, "a");
        m1.put(2, "b");
        console.println(m1);

        Map<Int, Test> m2 = new HashMap();
        m2.put(1, new Test("a"));
        m2.put(2, new Test("b"));
        console.println(m2);
        }

    class Test(String s)
        {
        @Override
        String toString()
            {
            return $"Test:{s}";
            }
        }
    }