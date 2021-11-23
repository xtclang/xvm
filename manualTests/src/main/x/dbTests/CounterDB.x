module CounterDB
        incorporates Database
    {
    package oodb import oodb.xtclang.org;

    import oodb.Database;
    import oodb.DBMap;
    import oodb.DBProcessor;
    import oodb.RootSchema;

    interface CounterSchema
            extends RootSchema
        {
        @RO DBMap<String, Int> counters;
        @RO Cranker cranker;
        }

    mixin Cranker
            into DBProcessor<String>
        {
        @Override
        void process(String name)
            {
            CounterSchema schema = dbRoot.as(CounterSchema);

            Int total = 0;
            total := schema.counters.get(name);
            schema.counters.put(name, ++total);

            if (total < 100)
                {
                schedule(name);
                }
            }
        }
    }
