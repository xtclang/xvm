import oodb.Transaction.TxInfo;

import storage.ObjectStore;

import Catalog.BuiltIn;


/**
 * The transaction manager is the clearinghouse for transactions. As a service, it naturally orders
 * the actions of concurrent transactions. When a transaction is instructed to commit, this service
 * causes the actual commit to occur, by issuing the instructions to the various enlisted
 * [ObjectStore] instances that were modified within the transaction.
 *
 * The transaction manager is not exposed; it's work is implied by the OODB interfaces, but it is
 * not part of the public API. It sits at the junction of three actors:
 *
 * * The [Catalog] creates the transaction manager, and holds on to it. The lifecycle of the
 *   transaction manager is controlled by the Catalog. When the Catalog transitions to its `Running`
 *   state, it enables the transaction manager, and as the Catalog leaves the `Running` state, it
 *   disables (and may eventually `close()`) the transaction manager. When the transaction manager
 *   is disabled, it immediately stops accepting new work, but it *will* attempt to complete the
 *   processing of any transactions that have already successfully prepared.
 *
 * * The [ObjectStore] instances each have a reference to the transaction manager, in order to ask
 *   it questions. The primary question is: "What is the read transaction ID that I should use for
 *   a specific write transaction ID?", which is used by the transaction manager as an indication
 *   to enlist the ObjectStore in the transaction, and to select the read transaction ID the first
 *   time that the question is asked for a particular write transaction ID.
 *
 * * The [Client] instances each have a reference to the transaction manager, in order to manage
 *   transaction boundaries. When a client requires a transaction, it creates it via
 *   [begin()](begin), it commits it via [commit()](commit), or rolls it back via
 *   [rollback()](rollback).
 *
 * The transaction manager could easily become a bottleneck in a highly concurrent system, so it
 * conducts its work via asynchronous calls as much as possible, usually by delegating work to
 * [Client] and [ObjectStore] instances.
 */
service TxManager(Catalog catalog)
        implements Closeable
    {
    construct(Catalog catalog)
        {
        this.catalog = catalog;
        }
    finally
        {
        reentrancy = Open;
        }


    // ----- properties ----------------------------------------------------------------------------

    enum Status {Initial, Enabled, Disabled, Closed}

    /**
     * The status of the transaction manager. For the most part, this could be handled with a simple
     * "Boolean enabled" flag, but the two additional book-end states provide more clarity for
     * the terminal points of the life cycle, and thus (in theory) better assertions.
     */
    public/private Status status = Initial;

    /**
     * The ObjectStore for each user defined DBObject in the `Catalog`. These provide the I/O for
     * the database.
     *
     * This data structure is lazily populated as necessary from the [Catalog], which is responsible
     * for creating and holding the references to each [ObjectStore].
     */
    private ObjectStore?[] appStores = new ObjectStore[];

    /**
     * The ObjectStore for each DBObject in the system schema.
     *
     * This data structure is lazily populated as necessary from the [Catalog], which is responsible
     * for creating and holding the references to each [ObjectStore].
     */
    private ObjectStore?[] sysStores = new ObjectStore[];

    /**
     * An illegal transaction ID used to indicate that no ID has been assigned yet.
     */
    static Int NO_TX = Int.minvalue;

    /**
     * The transaction id that was on disk from the last time that the database was closed down.
     */
    public/private Int lastClosedId = NO_TX;

    /**
     * The count of transactions (referred to as "write transactions") that this transaction manager
     * has created. This counter is used to provide unique write transaction IDs for each in-flight
     * transaction, and also for any trigger processing that occurs during the prepare phase of a
     * transaction. These values are not stored persistently; they only exist for the duration of an
     * in-flight transaction.
     *
     * When a new transaction manager is instantiated, the counter always  starts over at zero,
     * since the count and the ids are ephemeral.
     *
     * An ID is calculated as the negative of the count; for example, the first transaction would
     * increment the count to `1`, giving the transaction a "write tx id" of `-1`. The corresponding
     * "read tx id" is not assigned until an operation within the transaction attempts to perform a
     * read, at which point the transaction manager will provide the `lastPrepared` transaction as
     * the "read tx id".
     */
    public/private Int txCount = 0;

    /**
     * The transaction id that was last successfully prepared. There is a period between when a
     * transaction finishes preparing, and when it finishes committing, during which the transaction
     * can be used as the basis for subsequent transactions (to help minimize the chance of a
     * rollback caused by data changing), even though the transaction has not been verified to have
     * been committed to disk.
     *
     * Note: `lastPrepared >= lastCommitted`
     */
    public/private Int lastPrepared = 0;

    /**
     * The transaction id that was last successfully committed. When a database is newly created,
     * this value is zero; otherwise, this value is recorded on disk and restored when the
     * transaction manager reloads that persistent state.
     *
     * Note: `lastPrepared >= lastCommitted`
     */
    public/private Int lastCommited = 0;

    /**
     * The transactions that have begun, but have not yet committed or rolled back. The key is the
     * client id.
     */
    protected/private Map<Int, TxRecord> byClientId = new HashMap();

    /**
     * The transactions that have begun, but have not yet committed or rolled back, keyed by the
     * write transaction id.
     */
    protected/private Map<Int, TxRecord> byWriteId = new HashMap();

    /**
     * A **count** of the transactions that have begun, but have not yet committed or rolled back,
     * keyed by the read transaction id. The transaction manager uses this information to determine
     * when a historical read transaction can be discarded by the ObjectStore instances; as long as
     * a read transaction is in use by a write transaction, its information must be available.
     */
    protected/private Map<Int, Int> byReadId = new HashMap();

    // TODO Future<???> current prepare job


    // ----- lifecycle management ------------------------------------------------------------------

    /**
     * Allow the transaction manager to accept work from clients.
     *
     * This method is intended to only be used by the [Catalog].
     */
    void enable()
        {
        assert status == Initial || status == Disabled;

        using (val cs = new CriticalSection(Exclusive))
            {
            if (status == Initial)
                {
                // load the previously saved off transaction manager state from disk
                // TODO lastClosedId lastPrepared lastCommited
                }

            status = Enabled;
            }
        }

    /**
     * Verify that the transaction manager is open for client requests.
     *
     * @return True iff the transaction manager is enabled
     *
     * @throws IllegalState iff the transaction manager is not enabled
     */
    Boolean checkEnabled()
        {
        assert status == Enabled as $"TxManager is not enabled (status={status})";
        return True;
        }

    /**
     * Stop the transaction manager from accepting any work from clients, and roll back any
     * transactions that have not already prepared.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @param abort  pass True to abort everything that can still be aborted in order to disable as
     *               quickly as possible with minimal risk of failure
     */
    void disable(Boolean abort = False)
        {
        switch (status)
            {
            case Initial:
                status = Disabled;
                return;

            case Enabled:
                Exception? failure = Null;
                using (val cs = new CriticalSection(Exclusive))
                    {
                    if (!abort)
                        {
                        // commit everything that has already prepared
                        // TODO attempt to commit any prepared transactions
                        }

                    // abort everything that is left over
                    // TODO
                    }
                throw failure?;
                return;

            case Disabled:
            case Closed:
                return;
            }
        }

    @Override
    void close(Exception? cause = Null)
        {
        if (status == Enabled)
            {
            disable(abort = cause != Null);
            }

        status = Closed;
        }


    // ----- transactional API ---------------------------------------------------------------------

    /**
     * Begin a new transaction when it performs its first operation.
     *
     * This method is intended to only be used by the [Client].
     *
     * The transaction manager assigns a "write" temporary transaction id that the transaction will
     * use for all of its mutating operations (the "write TxId"). A "read" transaction id is
     * assigned the first time that the transaction attemptsm to read data. The read TxId ensures
     * that the client obtains a stable, transactional view of the underlying database, even as
     * other transactions from other clients may continue to be committed. All reads within that
     * transaction will be satisfied by the database using the version of the data specified by the
     * read TxId, plus whatever changes have occurred within the transaction using the write TxId.
     *
     * By forcing the client (the Connection, Transaction, and all DBObjects operate as "the Client
     * service") to call the transaction manager to obtain the write TxIds, and similarly by forcing
     * the ObjectStore implementations to obtain the read TxIds, the transaction manager acts as a
     * clearing-house for the lifecycle of each transaction, and for the lifecycle of transaction
     * management as a whole. As such, the transaction manager could (in theory) delay the creation
     * of a transaction, and/or re-order transactions, in order to optimize for some combination of
     * throughput, latency, and resource constraints.
     *
     * @param clientId  the Client's internal id
     * @param tx        the Client Transaction to assign TxIds for
     * @param readOnly  pass True to stipulate that the transaction must not mutate data
     * @param systemTx  indicates that the transaction is being conducted by the database system
     *                  itself, and not by an application "client"
     *
     * @return  the "write" transaction ID to use
     */
    Int begin(Int clientId, Client.Transaction tx, Boolean readOnly = False, Boolean systemTx = False)
        {
        checkEnabled();
        assert !byClientId.contains(clientId);

        Int      writeId = -(++txCount);
        TxRecord rec     = new TxRecord(tx, clientId, writeId);

        byClientId.put(clientId, rec);
        byWriteId.put(writeId, rec);

        return writeId;
        }

    /**
     * Attempt to commit an in-flight transaction.
     *
     * This method is intended to only be used by the [Client].
     */
    Boolean commit(Int clientId, Set<ObjectStore> enlisted)
        {
        checkEnabled();

        // validate the transaction
        assert TxRecord rec := byClientId.get(clientId);
        Set<ObjectStore> stores = rec.enlisted;
        if (stores.empty)
            {
            // empty transaction is committed without changing anything
            byClientId.remove(clientId);
            assert rec.readId == NO_TX;
            return True;
            }

        TODO

        // issue updates to the various ObjectStore instances
        return TODO
        }

    /**
     * Attempt to roll back an in-flight transaction.
     *
     * This method is intended to only be used by the [Client].
     *
     * @param clientId  the Client's internal id
     * @param enlisted  the ObjectStore instances that have been enlisted into the transaction
     *
     * @return True iff the Transaction was rolled back
     */
    Boolean rollback(Int clientId, Set<ObjectStore> enlisted)
        {
        checkEnabled();

        // validate the transaction
        assert TxRecord rec := byClientId.get(clientId);

        byClientId.remove(clientId);

        return True;
        }




    // ----- API for ObjectStore instances ---------------------------------------------------------

    /**
     * When an ObjectStore first encounters a transaction ID, it is responsible for enlisting itself
     * with the transaction manager for that transaction by calling this method.
     *
     * @param store  the ObjectStore instance that needs a base transaction ID (the "read" id) for
     *               the specified [txId] (the "write" id)
     * @param txId   the transaction ID that the client provided to the ObjectStore (the "write" id)
     *
     * @return the transaction id to use as the "read" transaction id; it identifies the transaction
     *         that the specified "write" transaction id is based on; note that the read transaction
     *         id will actually be a write transaction id while post-prepare triggers are being
     *         executed
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     * @throws IllegalState if the transaction state does not allow an ObjectStore to be enlisted,
     *         for example during rollback processing, or after a commit/rollback completes
     */
    Int enlist(ObjectStore store, Int txId)
        {
        // an enlist cannot occur while disabling the transaction manager, or once it has disabled
        checkEnabled();

        assert TxRecord tx := byWriteId.get(txId);

        Int readId = tx.readId;
        if (readId == NO_TX)
            {
            // this is the first ObjectStore enlisting for this transaction, so create a mutable
            // array to hold all the enlisting ObjectStores, as they each enlist
            assert tx.enlisted.empty;
            tx.enlisted = new HashSet<ObjectStore>();

            // use the last successfully prepared transaction id as the basis for reading within
            // this transaction; this reduces the potential for roll-back, since prepared
            // transactions always commit unless (i) the plug gets pulled, (ii) a fatal error
            // occurs, or (iii) the database is shut down abruptly
            readId = lastPrepared;
            assert readId != NO_TX;
            tx.readId = readId;
            }

        assert tx.status == InFlight && !tx.enlisted.contains(store);
        tx.enlisted.add(store);
        return readId;
        }

    /**
     * Obtain a [Client.Worker] for the specified transaction. A `Worker` allows CPU-intensive
     * serialization and deserialization work to be dumped back onto the client that is responsible
     * for the request that causes the work to need to be done.
     *
     * @param txId  a "write" transaction ID
     *
     * @return worker  the [Client.Worker] to offload serialization and deserialization work onto
     */
    Client.Worker workerFor(Int txId)
        {
        checkEnabled();
        assert TxRecord tx := byWriteId.get(txId);
        return tx.clientTx.outer.worker;
        }


    // ----- internal ------------------------------------------------------------------------------

    // REVIEW this is not used ... and has no doc
    protected void reload()
        {
        }

    /**
     * Discard any record of the specified transaction.
     *
     * @param clientId  the Client Transaction to unregister
     * @param enlisted  (optional) a set of ObjectStore instances that were enlisted into the
     *                  transaction
     */
    protected void release(Int clientId, Set<ObjectStore>? enlisted = Null)
        {
        if (TxRecord rec := byClientId.get(clientId))
            {
            byClientId.remove(clientId);
//
//            if (rec.readId == oldestReadId, --oldestReadCount <= 0)
//                {
//                if (byClientId.empty)
//                    {
//                    oldestReadId    = Int.minvalue;
//                    oldestReadCount = 0;
//                    }
//                else
//                    {
//                    // scan for the oldest read id and the number of occurrences thereof
//                    Int newOldest = Int.maxvalue;
//                    Int newCount  = 0;
//                    for (TxRecord scanRec : byClientId.values)
//                        {
//                        Int scanId = scanRec.readId;
//                        switch (scanId <=> newOldest)
//                            {
//                            case Lesser:
//                                newOldest = scanId;
//                                newCount  = 1;
//                                break;
//
//                            case Equal:
//                                ++newCount;
//                                break;
//                            }
//                        }
//
//                    // store the result of the scan
//                    assert newOldest >= 0 && newOldest != Int.maxvalue && newCount > 0;
//                    oldestReadId    = newOldest;
//                    oldestReadCount = newCount;
//                    }
//                }
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


    // ----- internal: TxRecord --------------------------------------------------------------------

    /**
     * This is the information that the TxManager maintains about each in flight transaction.
     */
    protected static class TxRecord(
            Client.Transaction  clientTx,
            Int                 clientId,
            Int                 writeId,
            Int                 readId    = NO_TX,
            Int                 prepareId = NO_TX,
            Set<ObjectStore>    enlisted  = [],
            Status              status    = InFlight)
        {
        /**
         * Enlist a transactional resource.
         *
         * @param store  the resource to enlist
         */
        void enlist(ObjectStore store)
            {
            if (enlisted.is(immutable Object))
                {
                enlisted = new HashSet(enlisted);
                }

            enlisted.add(store);
            }

        /**
         * Transaction status.
         */
        enum Status {InFlight, Preparing, Prepared, Committing, Committed, RollingBack, RolledBack}
        }
    }
