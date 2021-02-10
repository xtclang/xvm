module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        RootSchema         r = new RootSchema();
        Client<RootSchema> c = new Client();
        }

    class RootSchema
        {
        Int base;
        }

    class Client<Schema extends RootSchema>
        {
        construct()
            {
            assert Schema != RootSchema;
            }
        }
    }