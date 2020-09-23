class ClientTransaction<Schema extends db.RootSchema>
        extends ClientDBObject
        implements db.Transaction<Schema>
    {
    construct(ServerRootSchema dbSchema)
        {
        construct ClientDBObject(dbSchema);

        @Inject Clock clock;

        status   = Active;
        created  = clock.now;
        priority = Normal;
        contents = new HashMap();
        }

// ++ TODO GG: the two abstract props below are not needed
@Override
@Abstract @RO Schema schema;

@Override
@Abstract @RO (db.Connection<Schema> + Schema) connection;
// --
    @Inject Clock clock;

    @Override
    Duration transactionTime.get()
        {
        return clock.now - created;
        }

    @Override
    Duration commitTime.get()
        {
        TODO
        }

    @Override
    Int retryCount.get()
        {
        TODO
        }

    @Override
    Boolean commit()
        {
        // TODO
        return True;
        }

    @Override
    void rollback()
        {
        TODO
        }

    @Override
    void addCondition(db.Condition condition)
        {
        TODO
        }
    }