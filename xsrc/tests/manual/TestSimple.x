module TestSimple.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        import Ecstasy.collections.HashMap;

        console.println(foo("hello"));
        console.println(foo(0.toInt()));
        }

    Int foo(Object o)
        {
        Int j = 0;


        j += o.is(Int) ? o.toInt() : 4;
        j += !o.is(Int) ? 4 : o.toInt();
        return j;
        }

    }
