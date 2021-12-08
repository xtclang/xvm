module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    class Parent
        {
        class Child
            {
            Boolean garbage = False;

            // this used to compile without any errors
            @Override
            void trash()
                {
                }
            }
        }
    }