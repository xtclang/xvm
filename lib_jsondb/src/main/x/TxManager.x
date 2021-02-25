import storage.ObjectStore;

import Catalog.BuiltIn;


/**
 *
 */
service TxManager(Catalog catalog)
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The ObjectStore for each user defined DBObject in the `Catalog`. These provide the I/O for
     * the database.
     */
    protected/private ObjectStore?[] appStores = new ObjectStore[];

    /**
     * The ObjectStore for each DBObject in the system schema.
     */
    protected/private ObjectStore?[] sysStores = new ObjectStore[];

    /**
     * Exposes the last completed transaction ID so that connections can
     */
    @Atomic Int lastCompletedTx;
    // TODO @Atomic public/protected Int lastCompletedTx;

    /**
     * A fake transaction ID that is used to indicate that the [lastCompletedTx] should be used.
     */
    static Int USE_LAST_TX = -1;


    // ----- support ----------------------------------------------------------------------------

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    ObjectStore storeFor(Int id)
        {
        ObjectStore?[] stores = appStores;
        Int            index  = id;
        if (id < 0)
            {
            stores = sysStores;
            index  = BuiltIn.byId(id).ordinal;
            }

        Int size = stores.size;
        if (index < size)
            {
            return stores[index]?;
            }

        ObjectStore store = catalog.storeFor(id);

        // save off the ObjectStore (lazy cache)
        if (index > stores.size)
            {
            stores.fill(Null, stores.size..index);
            }
        stores[index] = store;

        return store;
        }


    // ----- transactional API ---------------------------------------------------------------------

    /**
     * Attempt to commit a transaction.
     */
    Boolean commit(Int clientId, Int txBase) // TODO changesByDBOid)
        {
        // validate the transaction
        TODO

        // issue updates to the various ObjectStore instances
        return TODO
        }

    }
