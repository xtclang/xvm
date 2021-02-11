module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        RootSchema         r = new RootSchema();
        Client<RootSchema> c = new Client();
        }

    const Info(String name);

    Info infoFor(Int i)
        {
        TODO
        }
    }