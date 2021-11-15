/**
 * This is a database schema for the Bank demo.
 *
 * To compile this database:
 *
 *     gradle compileOne -PtestName=Bank
 *
 * See `BankStressTest` app.
 */
module Bank
        incorporates Database
    {
    package oodb import oodb.xtclang.org;

    import oodb.Database;
    import oodb.DBCounter;
    import oodb.DBLog;
    import oodb.DBMap;
    import oodb.NoTx;
    import oodb.RootSchema;

    interface BankSchema
            extends RootSchema
        {
        @RO DBMap<Int, Account> accounts;

        @RO DBCounter holding; // in cents
// TODO @RO DBValue<Dec> holding;

        @RO @NoTx DBLog<String> log;

        // TODO validator
        // TODO rectifier
        // TODO distributor
        // TODO processor


//        const Overdraft(Int accountId, Int balance);
//
//        mixin OverdraftPenalizer
//                into DBProcessor<Overdraft>
//            {
//            @Override
//            void process(Overdraft overdraft)
//                {
//                //...
//                }
//            }
//
//        @RO OverdraftPenalizer penalties;
//
//        // somewhere else
//        penalties.add(new Overdraft(account.id, account.balance);
//        penalties.schedule(overdraft).every(Time:00:00).withPriority(Low);
//        penalties.schedule(overdraft, every=Time:00:00, priority=Low);
//        penalties.scheduleAfter(Duration:60s, new Overdraft(account.id, account.balance));

        Account openAccount(Int id, Int balance)
            {
            assert !accounts.contains(id) as $"account {id} already exists";
            assert balance > 0 as $"invalid opening balance: {format(balance)}";

            Account acc = new Account(id, balance);
            accounts.put(id, acc);

            holding.adjustBy(balance);
            return acc;
            }

        void closeAccount(Int id)
            {
            assert Account acc := accounts.get(id) as $"account {id} doesn't exists";

            accounts.remove(id);

            holding.adjustBy(-acc.balance);
            }

        void depositOrWithdraw(Int id, Int amount)
            {
            assert accounts.get(id) as $"account {id} doesn't exists";

            if (amount < 0)
                {
                assert accounts.require(id, e -> e.exists && e.value.balance + amount >= 0)
                    as $"not enough funds to withdraw {format(-amount)} from account {id}";
                }

            accounts.defer(id, e ->
                {
                if (e.exists && e.value.balance + amount >= 0)
                    {
                    e.value = e.value.changeBalance(amount);
                    return True;
                    }
                return False;
                });
            holding.adjustBy(amount);
            }

        void transfer(Int idFrom, Int idTo, Int amount)
            {
            assert idFrom != idTo as $"invalid transfer within an account";
            assert Account accFrom := accounts.get(idFrom) as $"account {idFrom} doesn't exists";
            assert Account accTo   := accounts.get(idTo)   as $"account {idTo} doesn't exists";
            assert amount > 0 as $"invalid transfer amount: {format(amount)}";

            // theoretically speaking, we could use the "require" here, e.g.:
            //     assert accounts.require(idFrom, e -> e.exists && e.value.balance >= amount)
            //          as $"not enough funds to transfer {format(amount)} from account {idFrom}";
            // however, it's not necessary since the "put" operations below enlist the corresponding
            // entries and would rollback if any change occurred to any of them, making the
            // "client-side" assert absolutely sufficient and the "server-side" requirement test
            // absolutely unnecessary

            assert accFrom.balance >= amount as
                $"not enough funds to transfer {format(amount)} from account {idFrom}";
            accounts.put(idFrom, accFrom.changeBalance(-amount));
            accounts.put(idTo,   accTo  .changeBalance(+amount));
            }

        Int audit()
            {
            Int holding = holding.get();
            Int total   = 0;
            for (Account acc : accounts.values)
                {
                Int balance = acc.balance;
                assert balance >= 0 as
                    $"audit failed: negative balance for account {acc.id}";
                total += balance;
                }

            assert total == holding as
                $"audit failed: expected={format(total)}, actual={format(holding)}";
            return total;
            }
        }

    static String format(Int amount)
        {
        (Int dollars, Int cents) = amount /% 100;
        return $"${dollars}.{cents.abs()}";
        }

    typedef (oodb.Connection<BankSchema> + BankSchema) Connection;

    typedef (oodb.Transaction<BankSchema> + BankSchema) Transaction;

    const Account(Int id, Int balance)
        {
        Account changeBalance(Int delta)
            {
            return new Account(id, balance + delta);
            }
        }
    }
