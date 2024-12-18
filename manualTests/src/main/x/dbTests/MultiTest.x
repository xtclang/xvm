/**
 * A stand-alone test for multi level schema.
 *
 * To run, from "./manualTests/" directory:
 *      xcc -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/MultiDB.x
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/MultiTest.x
 */
module MultiTest {
    package jsondb  import jsondb.xtclang.org;
    package multiDB import MultiDB;

    import multiDB.MainSchema;

    void run() {
        @Inject Console   console;
        @Inject Directory curDir;
        assert curDir.fileFor("src/main/x/dbTests/MultiDB.x").exists
                as "Not in \"manualTests\" directory";

        Directory buildDir = curDir.dirFor("build/xtc/main/lib");
        assert buildDir.fileFor("MultiDB.xtc").exists
                as "MultiDB must be compiled to the build/xtc/main/lib directory";

        Directory dataDir  = curDir.dirFor("data/multiDB").ensure();
        using (MainSchema schema = jsondb.createConnection("MultiDB", dataDir, buildDir).as(MainSchema)) {
            console.print($"{schema.counter.tick()=}");
            console.print($"{schema.child.counter.tick()=}");
        }
    }
}
