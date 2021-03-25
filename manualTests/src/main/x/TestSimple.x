module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        C c = new C();
        function (Boolean, String) (Boolean) f = c.foo;

        Tuple<Boolean, String> t1 = f(True);
        console.println($"f[1]={t1[1]}");

        Tuple<Boolean, String> t2 = f(False);
        console.println($"f[1]={t2[1]}");
        }

    class C
        {
        conditional String foo(Boolean f)
            {
            return f ? (True, "hello") : False;
            }
        }
    }