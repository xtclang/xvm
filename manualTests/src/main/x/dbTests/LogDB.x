@Database
module LogDB {
    package oodb import oodb.xtclang.org;

    import oodb.*;

    interface LogSchema
            extends RootSchema {
        @RO @NoTx @AutoTruncate(10K) @AutoExpire(Duration:1h) DBLog<String> logger;
        @RO CounterSchema counters;
    }

    interface CounterSchema
            extends DBSchema {
        @RO @NoTx DBCounter counter;
    }
}