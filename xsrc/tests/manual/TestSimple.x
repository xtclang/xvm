module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        Module ecstasy = this:module;
        console.println(ecstasy.qualifiedName);
        }
    }
