import oodb.Transaction.TxInfo;

import storage.ObjectStore;

import Catalog.BuiltIn;


/**
 * The transaction manager is the clearinghouse for transactions that want to begin reading, and for
 * transactions that are committing; its purpose is to order these things into a deliberate
 * sequence.
 *
 * REVIEW passing sets of ObjectStore instead of sets of Int ids ... pros and cons?
 */
service TxManager(Catalog catalog)
        implements Closeable
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
     * TODO Exposes the last completed transaction ID so that connections can
     */
    protected/private Int lastCompletedTx = USE_LAST_TX;  // REVIEW

    /**
     * The count of transactions registered with this TxManager.
     */
    protected/private Int registrationCounter = 0;


    /**
     * A fake transaction ID that is used to indicate that the [lastCompletedTx] should be used.
     */
    static Int USE_LAST_TX = -1;

    /**
     * This is the information that the TxManager maintains about each in flight transaction.
     */
    protected static class TxRecord(Client.Transaction tx, Int readId, Int writeId);

    /**
     * The transactions that have begun, but have not yet committed or rolled back. The key is the
     * client id.
     */
    protected/private Map<Int, TxRecord> inFlight = new HashMap();

    /**
     * The transactions that have begun, but have not yet committed or rolled back. The key is the
     * write transaction id.
     */
    protected/private Map<Int, TxRecord> byWriteId = new HashMap();

    /**
     * The cached read TxId that is the oldest read TxId that has been handed out but not yet
     * committed or rolled back. This could also be determined by scanning through [inFlight].
     */
    protected/private Int oldestReadId = Int.minvalue;

    /**
     * The number of times that [oldestReadId] has been handed out to transactions that have not yet
     * committed or rolled back. This could also be determined by scanning through [inFlight].
     */
    protected/private Int oldestReadCount = 0;


    // ----- transactional API ---------------------------------------------------------------------

    /**
     * Begin a new transaction when it performs its first operation.
     *
     * The transaction manager assigns a base transaction id that the transaction will use for all
     * of its read operations (the "read TxId"), and a temporary transaction id that the transaction
     * will use for all of its mutating operations (the "write TxId"). The read TxId ensures that
     * the client obtains a stable, transactional view of the underlying database, even as other
     * transactions from other clients may continue to be committed. All reads within that
     * transaction will be satisfied by the database using the version of the data specified by the
     * read TxId, plus whatever changes are occurred within the transaction using the write TxId.
     *
     * By forcing the client (the Connection, Transaction, and all DBObjects operate as "the Client
     * service") to call the transaction manager to obtain the read and write TxIds, the transaction
     * manager acts as a clearing-house for the creation of transactions. As such, the transaction
     * manager could potentially delay the creation of a transaction and re-order transactions in
     * order to optimize for some combination of throughput, latency, and resource constraints.
     *
     * @param clientId  the Client's internal id
     * @param tx        the Client Transaction to assign TxIds for
     *
     * @return readId   the transaction ID to use to read from the database
     * @return writeId  the transaction ID to use to perform mutating operations on the database
     */
    (Int readId, Int writeId) begin(Int clientId, Client.Transaction tx)
        {
        assert !inFlight.contains(clientId);

        // TODO? TxInfo txInfo   = tx.txInfo;

        Int readId  = lastCompletedTx;
        Int writeId = -1 - ++registrationCounter;

        TxRecord record = new TxRecord(tx, readId, writeId);
        inFlight.put(clientId, record);
        byWriteId.put(writeId, record);
        return readId, writeId;
        }

    /**
     * Attempt to commit a transaction.
     */
    Boolean commit(Int clientId, Set<ObjectStore> enlisted)
        {
        // validate the transaction
        TODO

        // issue updates to the various ObjectStore instances
        return TODO
        }

    /**
     * Attempt to commit a transaction.
     *
     * @param clientId  the Client's internal id
     * @param enlisted  the ObjectStore instances that have been enlisted into the transaction
     *
     * @return True iff the Transaction was rolled back
     */
    Boolean rollback(Int clientId, Set<ObjectStore> enlisted)
        {
        // validate the transaction
        if (TxRecord rec := inFlight.get(clientId))
            {
// TODO     rec.

            inFlight.remove(clientId);
            }

        return True;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Discard any record of the specified transaction.
     *
     * @param clientId  the Client Transaction to unregister
     * @param enlisted  (optional) a set of ObjectStore instances that were enlisted into the
     *                  transaction
     */
    protected void release(Int clientId, Set<ObjectStore>? enlisted = Null)
        {
        if (TxRecord rec := inFlight.get(clientId))
            {
            inFlight.remove(clientId);

            if (rec.readId == oldestReadId, --oldestReadCount <= 0)
                {
                if (inFlight.empty)
                    {
                    oldestReadId    = Int.minvalue;
                    oldestReadCount = 0;
                    }
                else
                    {
                    // scan for the oldest read id and the number of occurrences thereof
                    Int newOldest = Int.maxvalue;
                    Int newCount  = 0;
                    for (TxRecord scanRec : inFlight.values)
                        {
                        Int scanId = scanRec.readId;
                        switch (scanId <=> newOldest)
                            {
                            case Lesser:
                                newOldest = scanId;
                                newCount  = 1;
                                break;

                            case Equal:
                                ++newCount;
                                break;
                            }
                        }

                    // store the result of the scan
                    assert newOldest >= 0 && newOldest != Int.maxvalue && newCount > 0;
                    oldestReadId    = newOldest;
                    oldestReadCount = newCount;
                    }
                }
            }
        }

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


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null)
        {
        TODO
        }
    }
