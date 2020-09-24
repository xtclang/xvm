class ClientTransaction<Schema extends db.RootSchema>
        extends ClientDBObject
        implements db.Transaction<Schema>
    {
    construct(ServerRootSchema dbSchema)
        {
        construct ClientDBObject(dbSchema);
        }

// ++ TODO GG: the property below is not needed

@Override
@Abstract @RO (db.Connection<Schema> + Schema) connection;

@Override
@Abstract @RO db.SystemSchema sys;
// --

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
    }