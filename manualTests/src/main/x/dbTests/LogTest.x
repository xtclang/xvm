/**
 * A stand-alone test for DBLog functionality.
 *
 * To run, from "./manualTests/" directory:
 *      xcc -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/LogDB.x
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/LogTest.x
 */
module LogTest {
    package oodb   import oodb.xtclang.org;
    package jsondb import jsondb.xtclang.org;
    package logDB  import LogDB;

    import logDB.LogSchema;

    void run() {
        @Inject Directory curDir;
        assert curDir.fileFor("src/main/x/dbTests/LogDB.x").exists
                as "Not in \"manualTests\" directory";

        Directory buildDir = curDir.dirFor("build/xtc/main/lib");
        assert buildDir.fileFor("LogDB.xtc").exists
                as "LogDB must be compiled to the build/xtc/main/lib directory";

        Directory dataDir = curDir.dirFor("data/logDB").ensure();
        reportLogFiles(dataDir, "*** Before");

        using (LogSchema schema = jsondb.createConnection("LogDB", dataDir, buildDir).as(LogSchema)) {
            for (Int i : 1..1000) {
                schema.logger.add(
                    $"This is a message to test the log truncation policy: {schema.counters.counter.next()}");
            }
        }

        reportLogFiles(dataDir, "*** After");
    }

    void reportLogFiles(Directory dataDir, String prefix) {
        @Inject Console console;
        console.print(prefix);
        Int size = 0;
        for (File file : dataDir.dirFor("logger").files()) {
            console.print($"\t-> {file.size} {file.name}");
            size += file.size;
        }
        console.print($"total size {size} bytes");
    }
}