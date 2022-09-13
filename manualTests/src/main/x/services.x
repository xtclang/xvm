module TestServices
    {
    @Inject Console console;

    void run()
        {
        console.println("*** service tests ***\n");

        console.println($"{tag()} creating service");
        TestService svc = new TestService();

        TestService[] svcs = new Array<TestService>(4, (x) -> new TestService());

        console.println($|{tag()} calling service async/wait-style\
                         | {svc.serviceName} {svc.serviceControl.statusIndicator}
                         );
        Int n = svc.calcSomethingBig(new Duration(0));
        console.println($"{tag()} async/wait-style result={n}");

        Int n0 = svc.terminateExceptionally^("n0");
        &n0.handle(e ->
            {
            console.println($"{tag()} 4. expected exception={e.text}");
            return -1;
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

        Int n2 = svc.terminateExceptionally^("n2");
        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            console.println($"{tag()} 2. expected exception={e.text}");
            }

        assert &n2.assigned;
        &n2.handle(e ->
            {
            console.println($"{tag()} 3. expected exception={e.text}");
            return -1;
            });

        @Inject Timer timer;
        timer.start();

        Exception[] unguarded = new Exception[];
        using (new ecstasy.AsyncSection(unguarded.add))
            {
            Loop: for (TestService each : svcs)
                {
                val i = Loop.count;
                each.spin^(10_000).passTo(n ->
                    {
                    // TODO CP console.println($"{tag()} spin {Loop.count} yielded {n}; took {timer.elapsed.milliseconds} ms");
                    console.println($"{tag()} spin {i} yielded {n}; took {timer.elapsed.milliseconds} ms");
                    });
                }
            }
        assert unguarded.empty;

        // test timeout
        import ecstasy.Timeout;
        try
            {
            using (Timeout timeout = new Timeout(Duration:0.5S, True))
                {
                svc.calcSomethingBig(Duration:30M);
                assert;
                }
            }
        catch (TimedOut e)
            {
            }

        Int responded = 0;
        Int count     = 5;
        for (Int i : 0 ..< count)
            {
            console.println($"{tag()} calling service future-style: {i}");
            @Future Int result = svc.calcSomethingBig(Duration.ofSeconds(i));
            &result.whenComplete((n, e) ->
                {
                console.println($"{tag()} result={(n ?: e ?: "???")}");
                // when the last result comes back - shut down
                if (++responded == count)
                    {
                    svc.&done.set^(True);
                    }
                });
            }

        Boolean done = svc.waitForCompletion();
        console.println($"{tag()} done={done}; shutting down");

        // without the left side an exception would be reported by the default handler
        Int ignoreException = svc.calcSomethingBig^(Duration.ofMinutes(10));
        svc.serviceControl.shutdown();

        try
            {
            svc.spin(0);
            assert;
            }
        catch (Exception e)
            {
            console.println($"expected: {e}");
            }
        }

    service TestService
        {
        Int calcSomethingBig(Duration delay)
            {
            @Inject Console console;

            console.println($"{tag()} calculating for: {delay}");
            @Inject Timer timer;
            @Future Int   result;
            timer.schedule(delay, () ->
                {
                console.println($"{tag()} setting result {delay.seconds}");
                result=delay.seconds;
                });

            console.println($"{tag()} returning result");
            return result;
            }

        Int spin(Int iters)
            {
            Int sum = 0;
            for (Int i : iters..1)
                {
                sum += i;
                }

            return sum;
            }

        Int terminateExceptionally(String message)
            {
            throw new Exception(message);
            }

        @Future Boolean done;

        Boolean waitForCompletion()
            {
            return done;
            }
        }

    static Time now()
        {
        @Inject Clock clock;
        return clock.now;
        }

    static String tag()
        {
        static Time base = now();
        return $"{(now() - base).seconds}:\t" + (this:service.serviceName == "TestService" ? "[svc ]" : "[main]");
        }
    }