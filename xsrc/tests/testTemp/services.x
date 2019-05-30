module TestServices.xqiz.it
    {
    @Inject Console console;

    void run()
        {
        console.println("*** service tests ***\n");

        console.println($"{tag()} creating service");
        TestService svc = new TestService();

        console.println($"{tag()} calling service async/wait-style: {svc}");
        Int n = svc.calcSomethingBig(new Duration(0));
        console.println($"{tag()} async/wait-style result={n}");

        @Future Int n0 = svc.terminateExceptionally("n0");
        &n0.whenComplete((n, e) ->
            {
            assert e != null;
            console.println($"{tag()} 4. expected exception={e.text}");
            });

        try
            {
            Int n1 = svc.terminateExceptionally("n1");
            assert;
            }
        catch (Exception e)
            {
            console.println($"{tag()} 1. expected exception={e.text}");
            }

        @Future Int n2 = svc.terminateExceptionally("n2");
        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            console.println($"{tag()} 2. expected exception={e.text}");
            }

        &n2.whenComplete((n, e) ->
            {
            assert e != null;
            console.println($"{tag()} 3. expected exception={e.text}");
            });

        for (Int i : 0..4)
            {
            console.println($"{tag()} calling service future-style: {i}");
            @Future Int result = svc.calcSomethingBig(Duration.ofSeconds(i));
            &result.whenComplete((n, e) ->
                {
                console.println($"{tag()} result={(n ?: e ?: "???")}");
                });
            }

        @Future Boolean r = svc.notify(() ->
            {
            console.println($"{tag()} received notification");
            return False;
            });

        console.println($"{tag()} done {r}");
        }

    service TestService
        {
        Int calcSomethingBig(Duration delay)
            {
            @Inject Console console;

            console.println($"{tag()} calculating for: {delay}");
            @Inject Clock clock;
            @Future Int result;
            clock.schedule(delay, () ->
                {
                console.println($"{tag()} setting result {delay.seconds}");
                result=delay.seconds;
                });

            console.println($"{tag()} returning result");
            return result;
            }

        Int terminateExceptionally(String message)
            {
            throw new Exception(message);
            }

        Boolean notify(function Boolean () notify)
            {
            console.println($"{tag()} notify returned {notify()}");
            return True;
            }
        }

    static String tag()
        {
        return this:service.serviceName == "TestService" ? "[svc ]" : "[main]";
        }
    }