@Database
module CounterDB {
    package oodb import oodb.xtclang.org;

    import oodb.Database;
    import oodb.DBMap;
    import oodb.DBProcessor;
    import oodb.RootSchema;

    interface CounterSchema
            extends RootSchema {
        @RO Counters counters;
        @RO Cranker  cranker;
    }

    mixin Counters
            into DBMap<String, Int> {
        Int update(String name) {
            Int count = getOrDefault(name, 0);
            put(name, ++count);
            return count;
        }
    }

    mixin Cranker
            into DBProcessor<String> {
        @Override
        void process(String name) {
            CounterSchema schema = dbRoot.as(CounterSchema);

            Int count = schema.counters.update(name);

            if (count % 20 != 0) {
                schedule(name);
            }

            if (count % 60 == 0) {
                schema.counters.remove(name);
            }
        }
    }
}