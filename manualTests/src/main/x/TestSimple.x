module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println();

        Manager mgr = new Manager();
        Boolean result = mgr.commit();
        console.println($"commit={result}");
        }

    service Manager
        {
        Store[] stores = new Store[5](i -> new Store(i));

        Boolean commit()
            {
            Boolean prepareResult = stage^("prepare");
            FutureVar<Boolean> result = &prepareResult;

            result = result.transform(ok -> ok && stage("validators"));
            result = result.transform(ok -> ok && stage("rectifiers"));

            commitAsync^().handle(e ->
                {
                console.println($"Exception occurred during commitAsync {e}");
                return Tuple:();
                });

//            Tuple x = commitAsync^();
//            &x.handle(e ->
//                {
//                console.println($"Exception occurred during commitAsync {e}");
//                });
//
//            return result.thenDo(() ->
//                {
//                commitAsync^().handle(e ->
//                    {
//                    console.println($"Exception occurred during commitAsync {e}");
//                    return Tuple:();
//                    });
//                });
//
            return result;
            }

        Boolean stage(String step)
            {
            @Future Boolean result;

            Int count = stores.size;
            for (Store store : stores)
                {
                Boolean partial = store.process^(step);
                &partial.thenDo(() ->
                    {
                    if (--count == 0)
                        {
                        result = True;
                        }
                    });
                }

            return result;
            }

        void commitAsync()
            {
            console.println($"Committing");

            stage("committing");
            }
        }

    service Store(Int storeId)
        {
        Boolean process(String step)
            {
            @Inject Timer timer;
            @Inject Random rnd;

            console.println($"Started {step} on store {storeId}");

            @Future Boolean result;

            Duration delay = Duration.ofMillis(rnd.int(500) + 10);
            timer.schedule(delay, () ->
                {
                console.println($"Finished {step} on store {storeId} after {delay.milliseconds}ms");
                result=True;
                });

            return result;
            }
        }
    }