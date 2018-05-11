module TestSimple.xqiz.it
    {
    @Inject X.io.Console console;

    Void run()
        {
        console.println("\nHello world!!!");
        }
    }


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