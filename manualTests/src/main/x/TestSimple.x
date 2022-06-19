module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println();

        String s = foo()().as(Wrong.Name);  // used to NPE
        }

    function Object() foo()
        {
        TODO
        }
    }