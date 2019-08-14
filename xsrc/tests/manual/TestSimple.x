module TestSimple.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        import Ecstasy.collections.HashMap;
        }

    void testIn()
        {
        @Inject X.io.Console console;

        console.print("Say something: ");

        String s = console.readLine();
        console.println(s);
        }
    }
