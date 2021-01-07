module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    class Test
            extends Test
        {
        }

    @M("Hello")
    conditional String foo()
        {
        return True, "hi";
        }


    mixin M(String greeting = "?")
            into Method
        {
        conditional String bar()
            {
            return True, greeting;
            }
        }
    }
