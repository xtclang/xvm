module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Object t = new TestApp();
        if (t.is(WebService))
            {
            console.println($"Is a WebService path={t.path}");
            }
        }

    mixin WebService(String path = "/")
            into Initializable
        {
        construct(String path)
            {
            console.println($"mixin construct {path}");
            this.path = path;
            }

        void start()
            {
            init();
            }
        }

    @WebService("/test")
    class TestApp
            extends BaseApp
        {
        construct()
            {
            console.println("main construct");
            construct BaseApp();
            }
        }

    @WebService("/base")
    class BaseApp
            implements Initializable
        {
        construct()
            {
            console.println("base construct");
            }

        @Override
        void init()
            {
            }
        }

    interface Initializable
        {
        void init()
            {
            }
        }
    }
