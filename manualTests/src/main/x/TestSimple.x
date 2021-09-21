module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        assert:debug;

        function void() f = () ->
            {
            new C();
            };

        f();
        }

    class C
        {
        construct()
            {
            x = "construct";
            }
        finally
            {
            x = "finally";
            }

        assert()
            {
            x = "assert";
            }

        String x;

        Int y =
            {
            console.println("y.default");
            return 7;
            };

        @Lazy Boolean z.calc()
            {
            console.println("z.calc");
            return True;
            }
        }
    }
