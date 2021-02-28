import oodb.Transaction.TxInfo;

import storage.ObjectStore;

import Catalog.BuiltIn;


/**
 * The transaction manager is the clearinghouse for transactions that want to begin reading, and for
 * transactions that are committing; its purpose is to order these things into a deliberate
 * sequence.
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
     protected/private Int lastCompletedTx = USE_LAST_TX;  // REVIEW

    /**
     * A fake transaction ID that is used to indicate that the [lastCompletedTx] should be used.
     */
    static Int USE_LAST_TX = -1;


    // ----- transactional API ---------------------------------------------------------------------

    /**
     * When a transaction is performing its first read operation, it requires a base transaction id
     * to use to read from the database. All reads for the transaction will be satisfied by the
     * database using the version of the data specified by the base transaction id.
     *
     * By having the client (either the Connection or the Transaction, operating within the Client
     * service) call this transaction manager to obtain the base transaction id, the transaction
     * manager acts as a clearing house for the creation of transactions, and could theoretically
     * choose to delay the creation of a transaction based on other in-flight transactions, or based
     * on lifecycle events occurring within the database.
     *
     * @return the transaction ID to use to read from the database
     */
    Int selectBaseTxId(TxInfo info)
        {
        return lastCompletedTx;
        }

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


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    protected ObjectStore storeFor(Int id)
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
    }
