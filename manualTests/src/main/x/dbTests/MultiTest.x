/**
 * A stand-alone test for multi level schema.
 *
 * To run:
 *      xcc -o build -L build src/main/x/dbTests/MultiDB
 *      xec -o build -L build src/main/x/dbTests/MultiTest
 */
module MultiTest {
    package jsondb import jsondb.xtclang.org;

    package multiDB import MultiDB;

    import multiDB.MainSchema;

    void run() {
        @Inject Console console;
        @Inject Directory homeDir;

        Directory dataDir  = homeDir.dirFor("Development/xvm/manualTests/data/multiDB").ensure();
        Directory buildDir = homeDir.dirFor("Development/xvm/manualTests/build").ensure();

        using (MainSchema schema = jsondb.createConnection("MultiDB", dataDir, buildDir).as(MainSchema)) {
            console.print($"{schema.counter.tick()=}");
            console.print($"{schema.child.counter.tick()=}");
        }
    }
}
