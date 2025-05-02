/**
 * A stand-alone test for DBProcessor functionality.
 *
 * To run, from "./manualTests/" directory:
 *      xcc -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/CounterDB.x
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/CounterTest.x
 */
module CounterTest {
    package oodb      import oodb.xtclang.org;
    package jsondb    import jsondb.xtclang.org;
    package counterDB import CounterDB;

    import counterDB.CounterSchema;

    typedef (oodb.Connection<CounterSchema> + CounterSchema) as Connection;

    @Inject Console   console;
    @Inject Directory curDir;
    @Inject Random    rnd;
    @Inject Clock     clock;

    void run(String[] args = []) {
        assert curDir.fileFor("src/main/x/dbTests/CounterDB.x").exists
                as "Not in \"manualTests\" directory";

        Directory buildDir = curDir.dirFor("build/xtc/main/lib");
        assert buildDir.fileFor("CounterDB.xtc").exists
                as "CounterDB must be compiled to the build/xtc/main/lib directory";

        Directory  dataDir    = curDir.dirFor("data/counterDB").ensure();
        Connection connection = jsondb.createConnection("CounterDB", dataDir, buildDir).as(Connection);
        console.print(dump(connection));

        StringBuffer msg = new StringBuffer().append($"cranking up schedules: ");
        for (Int i : 1..3) {
            // pick a letter to schedule
            String name = ('A' + rnd.uint(26).toUInt32()).toString();
            connection.cranker.schedule(name);
            msg.append(name).append(", ");
        }
        console.print(msg.truncate(-2).toString());
        wait(connection, Duration:1s);
    }

    void wait(Connection connection, Duration duration) {
        @Future Tuple<> result;
        clock.schedule(duration, () -> {
            console.print(dump(connection));
            try {
                connection.close();
            } catch (Exception ignore) {}
            result=();
        });
        return result;
    }

    String dump(Connection connection) {
        try (val tx = connection.createTransaction()) {
            StringBuffer buf = new StringBuffer();
            for ((String name, Int count) : connection.counters) {
                buf.append($"{name}={count}, ");
            }
            return buf.toString().quoted();
        }
    }
}