/**
 * This is a simple console-based db test. To run it, use the following commands:
 *
 *  cd welcome
 *  xtc -o server/build -L server/build server/main/x/welcomeTest.x
 *  xec -L server/build server/main/x/welcomeTest
 */
module welcomeTest {
    package jsondb import jsondb.xtclang.org;
    package db import welcomeDB.examples.org;

    import db.WelcomeSchema;

    void run() {
        @Inject Console console;
        @Inject Directory curDir;

        Directory dataDir  = curDir.dirFor("data").ensure();
        Directory buildDir = curDir.dirFor("build").ensure();

        using (WelcomeSchema schema =
                jsondb.createConnection(db.qualifiedName, dataDir, buildDir).as(WelcomeSchema)) {
            console.print($"Welcome! You are guest #{schema.count.next()}");
        }
    }
}
