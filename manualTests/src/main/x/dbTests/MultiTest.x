/**
 * A stand-alone test that uses XUnit and XUnit-DB to test a multi level schema.
 *
 * To run, from "./manualTests/" directory:
 *      xtc build -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/MultiDB.x
 *      xtc test -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/MultiTest.x
 */
module MultiTest {
    package oodb   import oodb.xtclang.org;
    package multiDB import MultiDB;
    package xunit   import xunit.xtclang.org;
    package xunitdb import xunit_db.xtclang.org;

    import multiDB.ChildSchema;
    import multiDB.Connection;

    @Inject Connection connection;

    @Test
    void shouldTickInMainSchema() {
        Int current  = connection.counter.get();
        Int previous = connection.counter.tick();
        assert previous == current;
        Int after = connection.counter.get();
        assert after == current + 1;
    }

    @Test
    void shouldRollbackTickInMainSchema() {
        Int before = connection.counter.get();
        using (var tx = connection.createTransaction()) {
            connection.counter.tick();
            tx.rollback();
        }
        assert connection.counter.get() == before;
    }

    @Test
    void shouldTickInChildSchema() {
        ChildSchema child    = connection.child;
        Int         current  = child.counter.get();
        Int         previous = child.counter.tick();
        assert previous == current;
        Int after = child.counter.get();
        assert after == current + 2;
    }

    @Test
    void shouldRollbackTickInChildSchema() {
        ChildSchema child  = connection.child;
        Int         before = child.counter.get();
        using (var tx = connection.createTransaction()) {
            child.counter.tick();
            tx.rollback();
        }
        assert child.counter.get() == before;
    }
}
