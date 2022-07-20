module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Hello");

        Test test = new Test();

        @Future Tuple<Boolean, String> r1 = test.foo^();
        &r1.passTo(t ->
            {
            console.println(t);
            });

        @Future Tuple<Boolean, Int> r2 = test.bar^();
        &r2.passTo(t ->
            {
            if (t[0])
                {
                console.println(t[1]);
                }
            });
        }

    service Test
        {
        (Boolean, String) foo()
            {
            @Inject Timer timer;
            @Future String result;
            timer.schedule(Duration:1s, () ->
                {
                result = "42";
                });

            return True, result; // this used to throw a run-time exception
            }

        conditional Int bar()
            {
            @Inject Timer timer;
            @Future Int result;
            timer.schedule(Duration:1s, () ->
                {
                result = 42;
                });

            return True, result; // this used to throw a run-time exception
            }
        }
    }