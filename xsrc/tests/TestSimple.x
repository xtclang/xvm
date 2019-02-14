module TestSimple.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        test1();
        test2();
        test3();
        // test4(); // it works; but an interactive test is a nuisance to run
        }

    void test1()
        {
        console.println("Hello world!!!");
        }

    void test2(String s = "again!!!")
        {
        @Inject X.io.Console console;
        @Inject X.Clock runtimeClock;

        console.println(runtimeClock.now.to<String>() + ": Hello " + s);
        }

    void test3()
        {
        @Inject X.io.Console console;

        Int i = 40;
        i = i + 2;
        console.println(i);
        }

    void test4()
        {
        @Inject X.io.Console console;

        console.print("Say something: ");

        String s = console.readLine();
        console.println(s);
        }
    }
