@Database
module LogDB
    {
    package oodb import oodb.xtclang.org;

    import oodb.*;

    interface LogSchema
            extends RootSchema
        {
        @RO @NoTx @AutoTruncate(10K) @AutoExpire(Duration:1h) DBLog<String> logger;
        @RO @NoTx DBCounter counter;
        }
    }