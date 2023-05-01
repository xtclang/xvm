module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test();
        console.print(t.foo());
        }

    mixin Mix into Test
        {
        Int foo()
            {
            return super(); // this used to compile and blow at run-time
            }
        }

    class Test incorporates Mix
        {
        @Override
        Int foo()
            {
            return super();
            }
        }
    }