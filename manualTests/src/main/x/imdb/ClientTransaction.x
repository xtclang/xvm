@Abstract
class ClientTransaction<Schema extends db.RootSchema>
        extends ClientDBObject
        implements db.Transaction<Schema>
    {
    construct(ServerRootSchema dbSchema, db.DBTransaction dbTransaction)
        {
        construct ClientDBObject(dbSchema);

        this.dbTransaction = dbTransaction;
        }

    protected db.DBTransaction dbTransaction;

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