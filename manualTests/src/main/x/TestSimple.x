module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        C c = new C();
        function conditional String (Boolean) f = c.foo;

        Tuple<Boolean, String> t1 = f(True);
        console.println($"f[1]={t1[1]}");

        try
            {
            Tuple<Boolean, String> t2 = f(False);
            console.println($"f[1]={t2[1]}");
            }
        catch (Exception e)
            {
            console.println($"f[1]={e}");
            }

        function conditional String () f3 = c.&foo(True);
        Tuple<Boolean, String> t3 = f3();
        console.println($"f3[1]={t3[1]}");

        try
            {
            function conditional String () f4 = c.&foo(False);
            Tuple<Boolean, String> t4 = f4();
            console.println($"f4[1]={t4[1]}");
            }
        catch (Exception e)
            {
            console.println($"f4[1]={e}");
            }
        }

    class C
        {
        conditional String foo(Boolean f)
            {
            return f ? (True, "hello") : False;
            }
        }
    }