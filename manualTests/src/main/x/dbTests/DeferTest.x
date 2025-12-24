/**
 * A test to recreate an issue calling DBMap.defer()
 *
 * Run from the manualTests directory:
 *
 *    xtc build -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DeferDB.x
 *    xtc run -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DeferTest.x
 */
module DeferTest {
    package oodb    import oodb.xtclang.org;
    package jsondb  import jsondb.xtclang.org;
    package deferdb import DeferDB;

    import deferdb.Account;
    import deferdb.BankSchema;

    void run() {
        @Inject Directory curDir;
        assert curDir.fileFor("src/main/x/dbTests/DeferDB.x").exists
                as "Not in \"manualTests\" directory";

        Directory buildDir = curDir.dirFor("build/xtc/main/lib");
        assert buildDir.fileFor("DeferDB.xtc").exists
                as "DeferDB must be compiled to the build/xtc/main/lib directory";

        Directory dataDir = curDir.dirFor("data/deferDB").ensure();

        using (BankSchema schema = jsondb.createConnection("DeferDB", dataDir, buildDir).as(BankSchema)) {
            Account account = new Account(1, 1000);
            schema.accounts.put(1, account);

            // auto-commit
            schema.depositOrWithdraw(1, 100);

            assert Account fromDB := schema.accounts.get(1);
            assert fromDB.balance == 1100;

            // explicit transaction
            using (schema.createTransaction()) {
                schema.depositOrWithdraw(1, 100);
            }

            assert fromDB := schema.accounts.get(1);
            assert fromDB.balance == 1200;
        }
    }
}