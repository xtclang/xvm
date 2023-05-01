module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    mixin Mix into Test
        {
        }

    class Test incorporates Mix // this used to assert while building the TypeInfo
        {
        }
    }