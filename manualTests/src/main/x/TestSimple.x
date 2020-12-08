module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestService svc = new TestService();

        try
            {
            svc.terminateExceptionally("implicit sync");
            }
        catch (Exception e)
            {
            console.println($"expected exception={e.text}");
            }

        try
            {
            svc.terminateExceptionally("implicit sync with delay", 200);
            }
        catch (Exception e)
            {
            console.println($"expected exception={e.text}");
            }

        console.println("finished");
        }

    service TestService
        {
        Int terminateExceptionally(String message, Int ops = 0)
            {
            while (ops-- > 0)
                {
                }
            throw new Exception(message);
            }
        }
    }
