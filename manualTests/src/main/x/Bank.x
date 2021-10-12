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
        @RO @NoTx DBLog<String> log;

        Account openAccount(Int id, Int balance)
            {
            assert !accounts.contains(id) as $"account {id} already exists";

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
            assert Account acc := accounts.get(id) as $"account {id} doesn't exists";

            assert acc.balance + amount >= 0 as
                $"not enough funds to withdraw {format(amount)} from account {id}";

            accounts.put(id, acc.changeBalance(amount));
            holding.adjustBy(amount);
            }

        void transfer(Int idFrom, Int idTo, Int amount)
            {
            assert idFrom != idTo as $"invalid transfer within an account";
            assert Account accFrom := accounts.get(idFrom) as $"account {idFrom} doesn't exists";
            assert Account accTo   := accounts.get(idTo)   as $"account {idTo} doesn't exists";
            assert amount > 0 as $"invalid transfer amount: {format(amount)}";

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
                    $"audit failed: negatoive balance for account {acc.id}";
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
