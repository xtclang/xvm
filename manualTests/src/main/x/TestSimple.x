module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Class clz = Test;

        assert clz.is(LoginRequired) && clz.trust == "None";
        }

    mixin LoginRequired(String trust)
            into Class<Service>
        {}

    @LoginRequired("None")
    class Test // this used to throw an assertion during validation (should be "service")
        {
        }
    }