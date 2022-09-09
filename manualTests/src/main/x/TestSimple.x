module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    @Default // this used to compile without the Endpoint
    void test()
        {
        TODO
        }

    @Default @Get("")
    void test2()
        {
        TODO
        }

    mixin Get(String template)
            extends Endpoint(template)
        {
        }

    mixin Endpoint(String template)
            into Method
        {
        }

    mixin Default
            into Endpoint | service
        {
        }
    }