module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method m = Test.f1;
        console.println(m.as(Produces).produces);
        }

    mixin Endpoint(String path="")
        into Method;


    mixin Produces(String produces)
        into Endpoint;

    mixin Consumes(String consumes)
        into Endpoint;

    mixin Get(String path = "")
            extends Endpoint(path);

    service Test
        {
        @Consumes("c") // this used to fail to compile
        @Produces("p") // this used to fail to compile
        @Get("/")
        void f1()
            {
            }
        }
    }