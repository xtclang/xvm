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

    void test2()
        {
        @Inject X.io.Console console;

        DEBUG;
        String s = "Hello";
        console.println(s + " again!!!");
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

/*
@CmdLine module TestComplex.xqiz.it
    {
    @Handler
    static service CmdHandler
        {
        Int c;
        @Cmd("count")
        void counter()
            {
            console.println("count=" + ++c);
            }

        @Cmd("echo")
        void echo(String s)
            {
            console.println(s);
            }

        @Cmd("exit")
        void doExit()
            {
            stop();
            }
        }
    }

// somewhere else

interface CommandHandler
    {
    void run();
        {
        }

    void process(String cmd)
        {
        run();
        }
    }

mixin CmdLine
    into Module
    implements CommandHandler
    {
    Boolean oneAndDone = true;

    void run()
        {
        Class clzModule = this._meta._class;

        Class[] classes = clzModule.classes;
        for (Class clz : classes)
            {
            if (clz.category == Category.Service)
                {
                for (Composition step : clz.composition)
                    {
                    if (step.action == Annotates && step.ingredient.is(CmdHandler))
                        {
                        instantiateAndCall(clz);
                        return;
                        }
                    }
                }
            }
        }
    void shutdown() {...}
    }
*/