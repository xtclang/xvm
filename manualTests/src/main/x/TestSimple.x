module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    class Test
        {
        @Special("c1")
        construct()
            {
            }

        @Special("m1")
        void meth(Int i)
            {
            }

        @Special("f1")
        static void func(Int i)
            {
            }
        }

    mixin Special(String path)
            into Method
        {
        }
    }