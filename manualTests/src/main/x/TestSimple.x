module TestSimple
    {
    @Inject Console console;

    void run()
        {
        import ecstasy.reflect.Annotation;

        Type t = TestSimple;

        for (Method m : t.methods)
            {
            Annotation[] annos = m.annotations;
            if (annos.size > 0)
                {
                console.println($"{annos} {m}");
                }
            }

        for (Property p : t.properties)
            {
            Annotation[] annos = p.annotations;
            if (annos.size > 0)
                {
                console.println($"{annos} {p}");
                }
            }
        }


    @Path("method")
    void test()
        {
        }

    @Path("prop")
    Int depth = 0;

    mixin Path(String target = "")
            into Method | Property
        {
        }
    }
