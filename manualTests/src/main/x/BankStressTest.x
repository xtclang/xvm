module BankStressTest
    {
    package Bank import Bank;

    import Bank.Account;
    import Bank.Connection;

    static Int BRANCHES     = 1;
    static Int MAX_ACCOUNTS = 10;

    void run()
        {
        assert:debug;
        Duration openFor  = Duration.ofMinutes(15);
        Branch[] branches = new Branch[BRANCHES](i -> new Branch(i.toUInt64()));
        for (Branch branch : branches)
            {
            branch.doBusiness^(openFor);
            }
        wait(branches, openFor + Duration.ofSeconds(30)); // give them extra time to close naturally
        }

    Boolean wait(Branch[] branches, Duration duration)
        {
        @Inject Timer timer;

        // @Future Tuple<> result; // TODO GG: not allowed to return into void
        @Future Boolean result;
        FutureVar<Boolean> resultVar = &result; // TODO GG: moving this below line "result=True" screws up the compiler

        // schedule a "forced shutdown"
        timer.schedule(duration, () ->
            {
            @Inject Connection bank;

            if (!&result.assigned)
                {
                bank.log.add("Shutting down the test");

                // result=Tuple:();
                result=True;
                }
            });

        private void checkOpen(Branch[] branches, Timer timer, FutureVar<Boolean> result)
            {
            for (Branch branch : branches)
                {
                // wait until all branches are closed
                if (branch.status == Open)
                    {
                    timer.schedule(Duration.ofSeconds(10), &checkOpen(branches, timer, result));
                    return;
                    }

                @Inject Connection bank;
                bank.log.add("All branches have closed");
                result.set(True);
                }
            }

        // TODO GG: timer.schedule(Duration.ofSeconds(10), &checkOpen(branches, timer, &result));
        // schedule a periodic check
        timer.schedule(Duration.ofSeconds(10), &checkOpen(branches, timer, resultVar));
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
            DateTime open     = clock.now;
            DateTime close    = open + duration;
            Random   rnd      = new ecstasy.numbers.PseudoRandom(branchId);

            status = Open;
            bank.log.add($"Branch {branchId} opened");

            business:
            while (True)
                {
                if (++tryCount % 100 == 0)
                    {
                    if (clock.now < close)
                        {
                        bank.log.add(
                            $|Branch {branchId} performed {txCount} transactions in \
                             |{(clock.now - open).seconds} seconds
                             );
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
                        case 0..4:
                            op = "OpenAccount";
                            if (!bank.accounts.contains(acctId))
                                {
                                bank.openAccount(acctId, 256_00);
                                txCount++;
                                }
                            break;

                        case 5..9:
                            op = "CloseAccount";
                            if (bank.accounts.contains(acctId))
                                {
                                bank.closeAccount(acctId);
                                txCount++;
                                }
                            break;

                        case 10..49:
                            op = "Deposit or Withdrawal";
                            if (Account acc := bank.accounts.get(acctId))
                                {
                                Int amount = rnd.boolean() ? acc.balance/2 : -acc.balance/2;
                                bank.depositOrWithdraw(acctId, amount);
                                txCount++;
                                }
                            break;

                        case 50..95:
                            op = "Transfer";
                            Int acctIdTo = rnd.int(MAX_ACCOUNTS);
                            if (acctIdTo != acctId,
                                    Account accFrom := bank.accounts.get(acctId),
                                    bank.accounts.contains(acctIdTo))
                                {
                                bank.transfer(acctId, acctIdTo, accFrom.balance / 2);
                                txCount++;
                                }
                            break;

                        default:
                            op = "Audit";
                            bank.log.add($"Audited amount: {Bank.format(bank.audit())}");
                            break;
                        }
                    }
                catch (Exception e)
                    {
                    bank.log.add($"{op} failed at {branchId}: {e}");
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
