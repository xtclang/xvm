module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestService svc = new TestService();
        console.println($"service={svc}; name={svc.serviceName}; reentrancy={svc.reentrancy}");

        console.println($"0: status={svc.statusIndicator}");

        @Future Int delayResult = svc.delay(Duration.ofSeconds(1));

        console.println($"1: status={svc.statusIndicator}");

        &delayResult.whenComplete((n, e) ->
            {
            console.println($"1a. status={svc.statusIndicator}");
            });

        @Future Int spinResult = svc.spin(10_000);

        console.println($"2: status={svc.statusIndicator}");

        &spinResult.whenComplete((n, e) ->
            {
            console.println($"2a. status={svc.statusIndicator}");
            });
        }

    service TestService
        {
        Int delay(Duration delay)
            {
            @Inject Clock clock;

            @Future Int result;
            clock.schedule(delay, () ->
                {
                result=delay.seconds;
                });
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
        }
    }