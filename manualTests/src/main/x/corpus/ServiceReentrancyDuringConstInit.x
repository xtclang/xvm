module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting Dima's test");


        Test[] svc = new Array<Test>(2, (x) -> new Test());

        for (Test s : svc) {
                @Future Int i = s.test();
                console.println(s);
        }

        console.println("Stopping");
        svc[0].stopped = True;
        Stop.stop(svc[1]);
        }

        service Test {

                Boolean stopped = false;
                Int i = 0;

                Int test() {
                        console.println("starting service");
                        while (!stopped) {
                                i++;
                        }
                        console.println($"stopped at {i}");
                        return i;
                }
        }

        static service Stop {
                void stop(Test test) {
                        test.stopped = True;
                }
        }
    }
