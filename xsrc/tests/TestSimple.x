module TestSimple.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        test1();
        test2();
        test3();
        // testIn(); // it works; but an interactive test is a nuisance to run
        }

    void test1()
        {
        console.println("Hello world!!!");
        }

    void test2()
        {
        @Inject X.io.Console console;

        Int i = answer;
        console.println("answer is " + i);
        }

    @Lazy Int answer.calc()
        {
        return 42;
        }

    void test3(String s = "again!!!")
        {
        @Inject X.io.Console console;
        @Inject X.Clock runtimeClock;

        console.println(runtimeClock.now.to<String>() + ": Hello " + s);
        }

    void test4()
        {
        @Inject X.io.Console console;

        console.print("Say something: ");

        String s = console.readLine();
        console.println(s);
        }
    }
