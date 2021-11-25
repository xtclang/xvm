module CounterTest
    {
    package CounterDB import CounterDB;

    import CounterDB.CounterSchema;
    import CounterDB.oodb.Connection;
    import CounterDB.oodb.DBMap;

    @Inject Console console;
    @Inject Random  rnd;
    @Inject Timer   timer;

    void run()
        {
        @Inject CounterSchema schema;

        // first, show the state
        dump(schema.counters);

        for (Int i : 1..3)
            {
            // pick a letter to schedule
            String name = 'a' + rnd.int(26) .toString();
            console.println($"cranking up {name}...");
            schema.cranker.schedule(name);
            }

        // last, show the state
        timer.schedule(Duration:5s, () -> dump(schema.counters));
        }

    void dump(DBMap<String, Int> map)
        {
        assert:debug;

        for ((String name, Int count) : map)
            {
            console.println($"{name}={count}");
            }
        }
    }
