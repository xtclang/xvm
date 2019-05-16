module TestServices.xqiz.it
    {
    @Inject Console console;

    void run()
        {
        console.println("*** service tests ***\n");

        console.println("[main] creating service");
        TestService svc = new TestService();

        console.println("[main] calling service async/wait-style: " + svc);
        Int n = svc.calcSomethingBig(new Duration(0));
        console.println("[main] async/wait-style result=" + n);

        @Future Int n0 = svc.terminateExceptionally("n0");
        &n0.whenComplete((n, e) ->
            {
            assert e != null;
            console.println("[main] 4. expected exception=" + e.text);
            });

        try
            {
            Int n1 = svc.terminateExceptionally("n1");
            assert;
            }
        catch (Exception e)
            {
            console.println("[main] 1. expected exception=" + e.text);
            }

        @Future Int n2 = svc.terminateExceptionally("n2");
        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            console.println("[main] 2. expected exception=" + e.text);
            }

        &n2.whenComplete((n, e) ->
            {
            assert e != null;
            console.println("[main] 3. expected exception=" + e.text);
            });

        for (Int i : 0..4)
            {
            console.println("[main] calling service future-style: " + i);
            @Future Int result = svc.calcSomethingBig(Duration.ofSeconds(i));
            &result.whenComplete((n, e) ->
                {
                console.println("[main] result=" + (n ?: e ?: "???"));
                });
            }

        }

    service TestService
        {
        Int calcSomethingBig(Duration delay)
            {
            @Inject Console console;

            console.println("[svc ] calculating for: " + delay);
            @Inject Clock clock;
            @Future Int result;
            clock.schedule(delay, () ->
                {
                console.println("[svc ] setting result");
                result=delay.seconds;
                });

            console.println("[svc ] returning result");
            return result;
            }

        Int terminateExceptionally(String message)
            {
            throw new Exception(message);
            }
        }
    }