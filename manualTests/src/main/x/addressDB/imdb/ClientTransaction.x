class ClientTransaction<Schema extends db.RootSchema>
        extends ClientDBObject
        implements db.Transaction<Schema>
    {
    construct(ServerRootSchema dbSchema, db.DBTransaction dbTransaction)
        {
        construct ClientDBObject(dbSchema);

        this.dbTransaction = dbTransaction;
        }

    db.DBTransaction dbTransaction;

// ++ TODO GG: the property below is not needed

@Override
@Abstract @RO (db.Connection<Schema> + Schema) connection;

@Override
@Abstract @RO db.SystemSchema sys;
// --

    @Override
    Boolean commit()
        {
        for (db.DBObject.Change change : dbTransaction.contents.values)
            {
            if (change.is(ClientDBMap.ClientChange) && !change.apply())
                {
                return False;
                }
            }
        return True;
        }

    @Override
    void rollback()
        {
        for (db.DBObject.Change change : dbTransaction.contents.values)
            {
            if (change.is(ClientDBMap.ClientChange))
                {
                change.discard();
                }
            }
        dbTransaction.rollbackOnly = True;
        }
    }