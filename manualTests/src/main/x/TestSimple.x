module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method m = test;
        assert Parameter p := m.findParam("name");
        assert p.is(QueryParam);
        console.println($"attribute={p.attribute}");
        }

    void test(@QueryParam(42) String name)
        {
        }

    mixin QueryParam(Int attribute)
            into Parameter
        {
        }
    }
