module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        // assert:debug;
        Duration openFor  = Duration.ofSeconds(20000);
        Branch[] branches = new Branch[10](i -> new Branch(i.toUInt64()));
        for (Branch branch : branches)
            {
            branch.doBusiness^(openFor);
            }
        wait(branches, openFor + Duration.ofSeconds(3));
        }

    Boolean wait(Branch[] branches, Duration duration)
        {
        @Inject Timer timer;

        // @Future Tuple<> result; // TODO GG: not allowed to return into void
        @Future Boolean result;
        FutureVar<Boolean> resultVar = &result; // TODO GG: moving this below line "result=True" screws up the compiler

        private void checkOpen(Branch[] branches, Timer timer, FutureVar<Boolean> result)
            {
            console.println("Check status");
            for (Branch branch : branches)
                {
                if (branch.status == Open)
                    {
                    timer.schedule(Duration.ofSeconds(1), &checkOpen(branches, timer, result));
                    return;
                    }

                console.println("All branches have closed");
                if (!&result.assigned)
                    {
                    result.set(True);
                    }
                }
            }

        // TODO GG: timer.schedule(Duration.ofSeconds(10), &checkOpen(branches, timer, &result));
        // schedule a periodic check
        timer.schedule(Duration.ofSeconds(1), &checkOpen(branches, timer, resultVar));

        // schedule a "forced shutdown"
        timer.schedule(duration, () ->
            {
            if (!&result.assigned)
                {
                console.println("Shutting down the test");

                // result=Tuple:();
                result=True;
                }
            });

        return result;
        }

    service Branch(UInt branchId)
        {
        @Inject Clock clock;

        enum Status {Initial, Open, Closed}
        public/private Status status = Initial;

        void doBusiness(Duration duration)
            {
            Int      tryCount = 0;
            DateTime open     = clock.now;
            DateTime close    = open + duration;
            DB       db       = new DB();
            Random   rnd      = new ecstasy.numbers.PseudoRandom(branchId);

            status = Open;
            console.println($"Branch {branchId} opened");

            business:
            while (True)
                {
                if (++tryCount % 10 == 0)
                    {
                    if (clock.now < close)
                        {
                        console.println(
                            $|Branch {branchId} performed {tryCount} transactions in \
                             |{(clock.now - open).seconds} seconds
                             );
                        }
                    else
                        {
                        break business;
                        }
                    }
                db.commit(Duration.ofMillis(rnd.int(1000)));
                }
            status = Closed;
            }
        }

    service DB
        {
        Boolean commit(Duration duration)
            {
            @Inject Timer timer;
            @Future Boolean result;

            // schedule a "forced shutdown"
            timer.schedule(duration, () ->
                {
                // result=Tuple:();
                result=True;
                });

            return result;
            }
        }
    }