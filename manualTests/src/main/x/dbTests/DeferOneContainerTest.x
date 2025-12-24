/**
 * Another test to recreate the DBMap.defer() issue.
 *
 * This test runs everything in a single container. It relies on the database module that was built
 * by the DeferTest.x module.
 *
 * To run the tests, first compile the DeferDB module:
 *
 * - Then compile and run the DeferTest.x module (which will fail) but it will have built the
 * - DeferDB_jsondb.xtc module which this test depends on.
 * - Finally run this test.
 *
 * Run from the manualTests directory:
 *
 *    xtc build -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DeferDB.x
 *    xtc run -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DeferTest.x
 *    xtc run -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DeferOneContainerTest.x
 *
 */
module DeferOneContainerTest {
    package oodb        import oodb.xtclang.org;
    package deferdb     import DeferDB;
    package deferjsondb import DeferDB_jsondb;
    package jsondb      import jsondb.xtclang.org;

    import deferdb.Account;
    import deferdb.Connection;
    import deferdb.BankSchema;

    import jsondb.Catalog;
    import jsondb.CatalogMetadata;

    import oodb.model.User;

    void run() {
        @Inject Directory curDir;
        assert curDir.fileFor("src/main/x/dbTests/DeferDB.x").exists
                as "Not in \"manualTests\" directory";

        Directory dataDir = curDir.dirFor("data/deferDB").ensure();

        using (Connection conn = createConnection(dataDir)) {
            Account account = new Account(1, 1000);
            conn.accounts.put(1, account);

            using(conn.createTransaction()) {
                conn.depositOrWithdraw(1, 100);
            }

            assert Account fromDB := conn.accounts.get(1);
            assert fromDB.balance == 1100;
        }
    }

    Connection createConnection(Directory dataDir) {
        CatalogMetadata meta    = deferjsondb.as(CatalogMetadata);
        Catalog         catalog = meta.createCatalog(dataDir);
        catalog.ensureOpenDB("DeferDB");
        return catalog.createClient(new User(1, "admin"), autoShutdown=True).conn ?: assert;
    }

}