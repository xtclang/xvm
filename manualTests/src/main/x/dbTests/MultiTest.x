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
    void shouldTickInChildSchema() {
        Int current  = connection.child.counter.get();
        Int previous = connection.child.counter.tick();
        assert previous == current;
        Int after = connection.child.counter.get();
        assert after == current + 2;
    }
}
