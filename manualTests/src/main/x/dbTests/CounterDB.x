@Database
module CounterDB {
    package oodb import oodb.xtclang.org;

    import oodb.Database;
    import oodb.DBMap;
    import oodb.DBProcessor;
    import oodb.RootSchema;

    interface CounterSchema
            extends RootSchema {
        @RO DBMap<String, Int> counters;
        @RO Cranker cranker;
    }

    mixin Cranker
            into DBProcessor<String> {
        @Override
        void process(String name) {
            CounterSchema schema = dbRoot.as(CounterSchema);

            DBMap<String, Int> counters = schema.counters;

            Int count = counters.getOrDefault(name, 0);
            counters.put(name, ++count);

            if (count % 20 != 0) {
                schedule(name);
            }

            if (count % 60 == 0) {
                counters.remove(name);
            }
        }
    }
}