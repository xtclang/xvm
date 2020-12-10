module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestService svc = new TestService();

        function void (Int?, Exception?) handler = (n, e) ->
            {
            assert e != Null;
            console.println($"expected exception={e.text}");
            };

        // direct call
        try
            {
            svc.terminateExceptionally("implicit sync");
            assert;
            }
        catch (Exception e)
            {
            console.println($"expected exception={e.text}");
            }

        // indirect call
        function Int (String) f = svc.&terminateExceptionally();
        try
            {
            f("implicit sync f");
            assert;
            }
        catch (Exception e)
            {
            console.println($"expected exception={e.text}");
            }

        Int n0 = svc.terminateExceptionally^("n0");
        &n0.whenComplete(handler);

        @Future Int n1 = svc.terminateExceptionally^("n1");
        &n1.whenComplete(handler);

        svc.terminateExceptionally^("n2").whenComplete(handler);

        Int n0f = f^("n0f");
        &n0f.whenComplete(handler);

        @Future Int n1f = f^("n1f");
        &n1f.whenComplete(handler);

        f^("n2f").whenComplete(handler);

        console.println("finished");
        }

    service TestService
        {
        Int terminateExceptionally(String message, Int ops = 200)
            {
            while (ops-- > 0)
                {
                }
            throw new Exception(message);
            }
        }
    }
