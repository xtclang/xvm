@Abstract
class ClientTransaction<Schema extends oodb.RootSchema>
        extends ClientDBObject
        implements oodb.Transaction<Schema>
    {
    construct(ServerRootSchema        dbSchema,
              oodb.DBTransaction      dbTransaction,
              oodb.Transaction.TxInfo txInfo)
        {
        construct ClientDBObject(dbSchema);

        dbTransaction_ = dbTransaction;
        this.txInfo    = txInfo;
        }

    protected oodb.DBTransaction dbTransaction_;

    @Override
    public/protected oodb.Transaction.TxInfo txInfo;

    @Override
    Boolean commit()
        {
        for (oodb.DBObject.TxChange change : dbTransaction_.contents.values)
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
        for (oodb.DBObject.TxChange change : dbTransaction_.contents.values)
            {
            if (change.is(ClientDBMap.ClientChange))
                {
                change.discard();
                }
            }
        dbTransaction_.rollbackOnly = True;
        }
    }