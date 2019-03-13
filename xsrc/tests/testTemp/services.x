module TestServices.xqiz.it
    {
    import X.Clock;
    import X.Duration;

    @Inject X.io.Console console;

    void run()
        {
        console.println("*** service tests ***\n");

        console.println("[main] creating service");
        TestService svc = new TestService();
        console.println("[main] calling service async/wait-style: " + svc);
        Int n = svc.calcSomethingBig(new Duration(0));
        console.println("[main] async/wait-style result=" + n);

        for (Int i : 0..2)
            {
            console.println("[main] calling service future-style: " + svc);
            @Future Int result = svc.calcSomethingBig(new Duration(0));
            &result.thenDo(() -> {console.println("[main] future-style result=" + result);});
            }
        }

    service TestService
        {
        Int calcSomethingBig(Duration delay)
            {
            @Future Int result;
            console.println("[svc ] calculating for: " + delay);
            @Inject Clock runtimeClock;
            runtimeClock.scheduleAlarm(delay, () -> {result=42;});
            console.println("[svc ] returning result");
            return result;
            }
        }
    }