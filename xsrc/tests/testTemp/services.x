module TestServices.xqiz.it
    {
    void run()
        {
        @Inject Console console;
        console.println("*** service tests ***\n");

        @Inject Timer timer;
        // timer.scheduleAlarm(Duration.ofSeconds(2), () -> {console.println("... 2 seconds later ...");});
        timer.scheduleAlarm(Duration.ofSeconds(2), foo);

        // never ever do this:
        while (timer.elapsed.seconds < 3)
            {
            this:service.yield();
            }

//        console.println("[main] creating service");
//        TestService svc = new TestService();
//        console.println("[main] calling service async/wait-style: " + svc);
//        Int n = svc.calcSomethingBig(new Duration(0));
//        console.println("[main] async/wait-style result=" + n);

//        for (Int i : 0..2)
//            {
//            console.println("[main] calling service future-style: " + svc);
//            @Future Int result = svc.calcSomethingBig(Duration.ofSeconds(i));
//            &result.thenDo(() -> {console.println("[main] future-style result=" + result);} );
//            }
        }

    static void foo()
        {
        @Inject Console console;
        console.println("hello world!");
        }

    service TestService
        {
        Int calcSomethingBig(Duration delay)
            {
            @Inject Console console;
            console.println("[svc ] calculating for: " + delay);
            @Inject Timer timer;
            @Future Int result;
            timer.scheduleAlarm(delay, () -> {result=42;});
            console.println("[svc ] returning result");
            return result;
            }
        }
    }