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
            @Inject Timer timer;
            @Future Int result;
            timer.scheduleAlarm(delay, () ->
                {
                console.println("[svc ] setting result");
                result=delay.seconds;
                });

            console.println("[svc ] returning result");
            return result;
            }
        }
    }