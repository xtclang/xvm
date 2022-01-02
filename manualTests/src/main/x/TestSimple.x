module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Map<Double, Object> map = new HashMap();
        map.put(3, 14);
        assert Object o := map.get(3);
        console.println(&o.actualType);

        Double d = 2;
        console.println(d.hashCode());
        }
    }
