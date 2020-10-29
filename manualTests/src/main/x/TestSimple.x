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
        void start()
            {
            init();
            }
        }

    @WebService("/test")
    class TestApp
            extends BaseApp
        {
        }

    @WebService("/base")
    class BaseApp
            implements Initializable
        {
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
