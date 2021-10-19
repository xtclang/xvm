module BankStressTest
    {
    package Bank import Bank;

    import Bank.Account;
    import Bank.Connection;

    static Int BRANCHES     = 2;
    static Int MAX_ACCOUNTS = 2;

    void run()
        {
        assert:debug;
        Duration openFor  = Duration.ofSeconds(3600);
        Branch[] branches = new Branch[BRANCHES](i -> new Branch(i.toUInt64()));
        for (Branch branch : branches)
            {
            branch.doBusiness^(openFor);
            }
        wait(branches, openFor + Duration.ofSeconds(5)); // give them extra time to close naturally
        }

    void wait(Branch[] branches, Duration duration)
        {
        @Inject Timer timer;

        @Future Tuple<> result;

        // schedule a "forced shutdown"
        timer.schedule(duration, () ->
            {
            if (!&result.assigned)
                {
                @Inject Connection bank;
                bank.log.add("Shutting down the test");
                result=Tuple:();
                }
            });

        private void checkOpen(Branch[] branches, Timer timer, FutureVar<Tuple> result)
            {
            for (Branch branch : branches)
                {
                // wait until all branches are closed
                if (branch.status == Open)
                    {
                    timer.schedule(Duration.ofSeconds(10), &checkOpen(branches, timer, result));
                    return;
                    }

                if (!result.assigned)
                    {
                    @Inject Connection bank;
                    bank.log.add("All branches have closed");
                    result.set(Tuple:());
                    }
               }
            }

        // schedule a periodic check
        timer.schedule(Duration.ofSeconds(10), &checkOpen(branches, timer, &result));
        return result;
        }

    service Branch(UInt branchId)
        {
        @Inject Connection bank;
        @Inject Clock      clock;

        enum Status {Initial, Open, Closed}
        public/private Status status = Initial;

        void doBusiness(Duration duration)
            {
            Int      tryCount = 0;
            Int      txCount  = 0;
            DateTime start    = clock.now;
            DateTime close    = start + duration;
            Random   rnd      = new ecstasy.numbers.PseudoRandom(branchId);

            status = Open;
            bank.log.add($"Branch {branchId} opened");

            business:
            while (True)
                {
                if (++tryCount % 100 == 0)
                    {
                    DateTime now = clock.now;
                    if (now < close)
                        {
                        bank.log.add(
                            $|Branch {branchId} performed {100 + txCount} transactions in \
                             |{(now - start).seconds} seconds
                             );
                        txCount = 0;
                        start   = now;
                        }
                    else
                        {
                        break business;
                        }
                    }

                String op = "";
                try
                    {
                    Int acctId = rnd.int(MAX_ACCOUNTS);
                    switch (rnd.int(100))
                        {
                        case 0..1:
                            op = "OpenAccount";
                            if (!bank.accounts.contains(acctId))
                                {
                                txCount++;
                                bank.openAccount(acctId, 256_00);
                                }
                            break;

                        case 2..3:
                            op = "CloseAccount";
                            if (bank.accounts.contains(acctId))
                                {
                                txCount++;
                                bank.closeAccount(acctId);
                                }
                            break;

                        case 4..49:
                            op = "Deposit or Withdrawal";
                            if (Account acc := bank.accounts.get(acctId))
                                {
                                txCount++;
                                Int amount = rnd.boolean() ? acc.balance/2 : -acc.balance/2;
                                bank.depositOrWithdraw(acctId, amount);
                                }
                            break;

                        case 50..98:
                            op = "Transfer";
                            Int acctIdTo = rnd.int(MAX_ACCOUNTS);
                            if (acctIdTo != acctId,
                                    Account accFrom := bank.accounts.get(acctId),
                                    bank.accounts.contains(acctIdTo))
                                {
                                txCount++;
                                bank.transfer(acctId, acctIdTo, accFrom.balance / 2);
                                }
                            break;

                        case 99:
                            op = "Audit";
                            txCount++;
                            bank.log.add($"Audited amount: {Bank.format(bank.audit())}");
                            break;
                        }
                    }
                catch (Exception e)
                    {
                    bank.log.add($"{op} failed at {branchId}: {e.text}");
                    if (op == "Audit")
                        {
                        break business;
                        }
                    }
                }

            bank.log.add($"Branch {branchId} closed");
            status = Closed;
            }
        }
    }
