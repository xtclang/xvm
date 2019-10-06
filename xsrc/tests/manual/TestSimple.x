module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject X.io.Console console;

    void run()
        {
        console.println(foo("hello"));
        }
    }
