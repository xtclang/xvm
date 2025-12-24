/**
 * This is a database schema for the Bank demo.
 */
@Database
module DeferDB {
    package oodb import oodb.xtclang.org;

    import oodb.Database;
    import oodb.DBMap;
    import oodb.RootSchema;

    interface BankSchema
            extends RootSchema {

        @RO DBMap<Int, Account> accounts;

        void depositOrWithdraw(Int id, Int amount) {
            assert accounts.get(id) as $"account {id} doesn't exists";

            if (amount < 0) {
                assert accounts.require(id, e -> e.exists && e.value.balance + amount >= 0)
                    as $"not enough funds to withdraw {format(-amount)} from account {id}";
            }

            accounts.defer(id, e -> {
                if (e.exists && e.value.balance + amount >= 0) {
                    e.value = e.value.changeBalance(amount);
                    return True;
                }
                return False;
            });
        }
    }

    static String format(Int amount) {
        (Int dollars, Int cents) = amount /% 100;
        return $"${dollars}.{cents.abs()}";
    }

    typedef (oodb.Connection<BankSchema>  + BankSchema) as Connection;

    const Account(Int id, Int balance) {
        Account changeBalance(Int delta) {
            return new Account(id, balance + delta);
        }
    }
}