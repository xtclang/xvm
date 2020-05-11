module TestSimple
    {
    import ecstasy.TypeSystem;
    import ecstasy.mgmt.Container.ApplicationControl;

    @Inject Console console;

    void run()
        {
        testParams();
        }

    void testParams(Int i=0, String... strings)
        {
        console.println(strings.size);

        for (String s : strings)
            {
            console.println(s);
            }
        }
    }