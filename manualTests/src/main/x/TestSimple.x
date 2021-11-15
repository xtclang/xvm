module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<Int, Int> map = new HashMap();
        map.put(0, 0);

        map.process(0, e -> e.value++); // this used to fail the compilation

        console.println(map);
        }
    }
