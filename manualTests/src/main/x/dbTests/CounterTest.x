/**
 * A stand-alone test for DBProcessor functionality.
 *
 * To run:
 *      gradle compileOne -PtestName=dbTests/CounterDB
 *      gradle runOne -PtestName=dbTests/CounterTest
 */
module CounterTest
    {
    package oodb   import oodb.xtclang.org;
    package jsondb import jsondb.xtclang.org;

    package counterDB import CounterDB;

    import counterDB.CounterSchema;

    typedef (oodb.Connection<CounterSchema> + CounterSchema) as Connection;

    void run(String[] args = [])
        {
        @Inject Console   console;
        @Inject Directory homeDir;
        @Inject Random    rnd;

        Directory dataDir  = homeDir.dirFor("Development/xvm/manualTests/data/counterDB").ensure();
        Directory buildDir = homeDir.dirFor("Development/xvm/manualTests/build").ensure();

        Connection connection =
                jsondb.createConnection("CounterDB", dataDir, buildDir).as(Connection);

        console.print(dump(connection));

        StringBuffer msg = new StringBuffer().append($"cranking up schedules: ");
        for (Int i : 1..3)
            {
            // pick a letter to schedule
            String name = ('A' + rnd.uint(26).toUInt32()).toString();

            connection.cranker.schedule(name);

            msg.append(name).append(", ");
            }
        console.print(msg.truncate(-2).toString());

        wait(connection, Duration:1s);
        }

    void wait(Connection connection, Duration duration)
        {
        @Inject Console console;
        @Inject Timer timer;

        @Future Tuple<> result;
        timer.schedule(duration, () ->
            {
            console.print(dump(connection));
            try
                {
                connection.close();
                }
            catch (Exception ignore) {}
            result=Tuple:();
            });
        return result;
        }

    String dump(Connection connection)
        {
        try (val tx = connection.createTransaction())
            {
            StringBuffer buf = new StringBuffer();
            for ((String name, Int count) : connection.counters)
                {
                buf.append($"{name}={count}, ");
                }
            return buf.toString().quoted();
            }
        }
    }