import collections.ArrayDeque;
import collections.SparseIntSet;

import oodb.RootSchema;
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
service TxManager<Schema extends RootSchema>(Catalog<Schema> catalog)
        implements Closeable
    {
    construct(Catalog<Schema> catalog)
        {
        this.catalog     = catalog;

        // build the quick lookup information for the optional transactional "modifiers"
        // (validators, rectifiers, distributors, and async triggers)
        Boolean hasValidators    = catalog.metadata?.dbObjectInfos.any(info -> !info.validators   .empty) : False;
        Boolean hasRectifiers    = catalog.metadata?.dbObjectInfos.any(info -> !info.rectifiers   .empty) : False;
        Boolean hasDistributors  = catalog.metadata?.dbObjectInfos.any(info -> !info.distributors .empty) : False;
        Boolean hasAsyncTriggers = catalog.metadata?.dbObjectInfos.any(info -> !info.asyncTriggers.empty) : False;
        Boolean hasSyncTriggers  = hasValidators |  hasRectifiers | hasDistributors;

        if (hasValidators)
            {
            validators = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.validators.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        if (hasRectifiers)
            {
            rectifiers = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.rectifiers.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        if (hasDistributors)
            {
            distributors = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.distributors.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        if (hasSyncTriggers)
            {
            asyncTriggers = new SparseIntSet()
                    .addAll(validators)
                    .addAll(rectifiers)
                    .addAll(distributors);
            }

        if (hasAsyncTriggers)
            {
            syncTriggers = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.asyncTriggers.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }
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

    /**
     * The set of ids of DBObjects that have validators.
     */
    protected/private Set<Int> validators = [];
    /**
     * The set of ids of DBObjects that have rectifiers.
     */
    protected/private Set<Int> rectifiers = [];
    /**
     * The set of ids of DBObjects that have distributors.
     */
    protected/private Set<Int> distributors = [];
    /**
     * The set of ids of DBObjects that have validators, rectifiers, and/or distributors.
     */
    protected/private Set<Int> syncTriggers = [];
    /**
     * The set of ids of DBObjects that have asynchronous triggers.
     */
    protected/private Set<Int> asyncTriggers = [];

    /**
     * A pool of artificial clients for processing triggers.
     */
    ArrayDeque<Client<Schema>> clientCache = new ArrayDeque();

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
     * Determine if the specified txId indicates a read ID, versus a write ID.
     *
     * @param txId  a transaction identity
     *
     * @return True iff the txId is in the range reserved for read transaction IDs
     */
    Boolean isReadTx(Int txId)
        {
        return txId > 0;
        }

    /**
     * Determine if the specified txId indicates a write ID, versus a read ID.
     *
     * @param txId  a transaction identity
     *
     * @return True iff the txId is in the range reserved for write transaction IDs
     */
    Boolean isWriteTx(Int txId)
        {
        return txId < 0;
        }

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
     * @param tx        the Client Transaction to assign TxIds for
     * @param readOnly  pass True to stipulate that the transaction must not mutate data
     * @param systemTx  indicates that the transaction is being conducted by the database system
     *                  itself, and not by an application "client"
     *
     * @return  the "write" transaction ID to use
     */
    Int begin(Client.Transaction tx, Boolean readOnly = False, Boolean systemTx = False)
        {
        checkEnabled();

        Int clientId = tx.outer.id;
        assert !byClientId.contains(clientId);

        Int      writeId = genWriteId();
        TxRecord rec     = new TxRecord(tx, tx.txInfo, clientId, writeId);

        byClientId.put(clientId, rec);
        byWriteId .put(writeId , rec);

        return writeId;
        }

    /**
     * Attempt to commit an in-flight transaction.
     *
     * This method is intended to only be used by the [Client].
     *
     * @param writeId   the "write" transaction id previously returned from [begin]
     *
     * @return True if the transaction was committed; False if the transaction was rolled back for
     *         any reason
     */
    Boolean commit(Int writeId)
        {
        checkEnabled();

        // validate the transaction
        assert TxRecord rec := byWriteId.get(writeId) as $"Missing TxRecord for txid={writeId}";
        assert rec.status == InFlight;

        // REVIEW should this protected with a try/catch?
        return prepare(rec)
            && (validators  .empty || validate  (rec))
            && (rectifiers  .empty || rectify   (rec))
            && (distributors.empty || distribute(rec))
                ? commit(rec)
                : rollback(rec);
        }

    /**
     * Attempt to roll back an in-flight transaction.
     *
     * This method is intended to only be used by the [Client].
     *
     * @param writeId  the "write" transaction id previously returned from [begin]
     *
     * @return True iff the Transaction was rolled back
     */
    void rollback(Int writeId)
        {
        checkEnabled();

        // validate the transaction
        assert TxRecord rec := byWriteId.get(writeId) as $"Missing TxRecord for txid={writeId}";
        assert rec.status == InFlight;

        rollback(rec);
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
            // set to hold all the enlisting ObjectStores, as they each enlist
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


    // ----- internal --------------------------------------------------------------------

    /**
     * Generate a write transaction ID.
     *
     * @return the new write id
     */
    protected Int genWriteId()
        {
        return -(++txCount);
        }

    /**
     * Generate the next "prepare" transaction ID. Note that until a transaction finishes TODO
     *
     * @return the new prepare id
     */
    protected Int genPrepareId()
        {
        return lastPrepared + 1;
        }

    /**
     * TODO
     */
    protected Client<Schema> allocateClient()
        {
        return clientCache.takeOrCompute(() -> catalog.createClient());
        }

    /**
     * TODO
     */
    protected void recycleClient(Client<Schema> client)
        {
        return clientCache.reversed.add(client);
        }


    /**
     * Attempt to prepare an in-flight transaction.
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean prepare(TxRecord rec)
        {
        Int              writeId = rec.writeId;
        Set<ObjectStore> stores  = rec.enlisted;
        if (stores.empty)
            {
            // an empty transaction is considered committed
            assert rec.readId == NO_TX || isWriteTx(rec.readId);
            terminate(rec, Committed);
            return True;
            }

        // prepare phase
        rec.status = Preparing;
        Int                 prepareId   = genPrepareId();
        Boolean             abort       = False;
        FutureVar<Boolean>? preparedAll = Null;
        for (ObjectStore store : stores)
            {
            // we cannot predict the order of asynchronous execution, so it is quite possible that
            // we find out that the transaction must be rolled back while we are still busy here
            // trying to get the various ObjectStore instances to prepare what changes they have
            if (abort)
                {
                // this will eventually resolve to False, since something has failed and caused the
                // transaction to roll back (which rollback may still be occurring asynchronously)
                break;
                }

            import ObjectStore.PrepareResult;
            @Future PrepareResult result      = store.prepare(writeId, prepareId);
            @Future Boolean       preparedOne = &result.transform(pr ->
                {
                switch (pr)
                    {
                    case FailedRolledBack:
                        stores.remove(store);
                        abort = True;
                        return False;

                    case CommittedNoChanges:
                        stores.remove(store);
                        return True;

                    case Prepared:
                        return True;
                    }
                });

            preparedAll = preparedAll?.and(&preparedOne, (f1, f2) -> f1 & f2) : &preparedOne;
            }

        assert preparedAll != Null;
        @Future Boolean result = preparedAll.whenComplete((v, e) ->
            {
            // check for successful prepare, and whether anything is left enlisted
            switch (v, stores.empty)
                {
                case (False, False):
                    // failed; remaining stores need to be rolled back
                    rollback(rec);
                    break;

                case (False, True ):
                    // failed; already rolled back
                    terminate(rec, RolledBack);
                    break;

                case (True , False):
                    // there might still be triggers to process as part of the prepare, but the
                    // "client" portion of the prepare has completed
                    if (rec.triggered.empty)
                        {
                        // no triggers; prepare is completed
                        rec.status = Prepared;
                        }
                    break;

                case (True , True ):
                    // succeeded; already committed
                    terminate(rec, Committed);
                    break;
                }
            });

// TODO
//        @Future Boolean triggered = syncTriggers.empty
//                ? &prepared
//                : &prepared.createContinuation(ok -> ok && triggerImpl(rec));

        return result;
        }

    /**
     * Attempt to prepare an in-flight transaction.
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean validate(TxRecord rec)
        {
        if (validators.empty)
            {
            return True;
            }

        TODO
        }

    /**
     * Attempt to prepare an in-flight transaction.   TODO
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean rectify(TxRecord rec)
        {
        if (rectifiers.empty)
            {
            return True;
            }

        TODO
        }

    /**
     * Attempt to prepare an in-flight transaction.TODO
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean distribute(TxRecord rec)
        {
        if (distributors.empty)
            {
            return True;
            }

        TODO
        }

    /**
     * Attempt to execute the synchronous triggers against a preparing transaction.
     *
     * @param rec  the TxRecord representing the transaction to run triggers against
     *
     * @return True iff the Transaction successfully evaluated its triggers
     */
    protected Boolean triggerImpl(TxRecord rec)
        {
        if (rec.triggered.empty)
            {
            return True;
            }
TODO
//        // some reasonable amount of trigger recursion should be expected; consider improving the
//        // error message here to provide some indication of how we got here, for example start
//        // keeping track of what is triggering what once we get close to the recursion limit
//        assert depth <= 64;
//
//        // the transaction record will hold on to a second transactional record that is only used
//        // during trigger processing, and which "commits to" (merges into) the existing transaction
//        // on each completion
//        TxRecord triggerRec;
//        Int      triggerId = rec.prepareId;
//        if (triggerId == NO_TX)
//            {
//            // we've already completed the preparation of the transaction based on the client's
//            // changes, so the trigger processing will occur based on the prepared transaction that
//            // will commit on top of the most recent transaction
//            rec.readId = lastPrepared;
//
//            triggerId  = genWriteId();
//            triggerRec = new TxRecord(tx, tx.txInfo, clientId, triggerId, readId=rec.writeId);
//
//            byWriteId.put(writeId, rec);
//
//            rec.prepareId = triggerId;
//            }
//        else
//            {
//            assert triggerRec := byWriteId.get(rec.prepareId);
//            }
//
//        FutureVar<Boolean>? triggeredAll = Null;
//        for (ObjectStore store : rec.takeTriggered())
//            {
//            // triggers are processed in sequence
//// TODO            @Future TODO result = store.prepare(writeId);
//            @Future Boolean triggeredOne = &result.transform(pr ->
//                {
//                TODO
//                });
//
//            triggeredAll = triggeredAll?.and(&triggeredOne, (f1, f2) -> f1 & f2) : &triggeredOne;
//            }
//
//        assert triggeredAll != Null;
//        @Future Boolean result = triggeredAll.whenComplete((v, e) ->
//            {
//            // check for successful prepare, and whether anything is left enlisted
//            switch (v, stores.empty)
//                {
//                case (False, False):
//                    // failed; remaining stores need to be rolled back
//                    rollback(rec);
//                    break;
//
//                case (False, True ):
//                    // failed; already rolled back
//                    terminate(rec, RolledBack);
//                    break;
//
//                case (True , False):
//                    // there might still be triggers to process as part of the prepare, but the
//                    // "client" portion of the prepare has completed
//                    if (rec.triggered.empty)
//                        {
//                        // no triggers; prepare is completed
//                        rec.status = triggered;
//                        }
//                    break;
//
//                case (True , True ):
//                    // succeeded; already committed
//                    terminate(rec, Committed);
//                    break;
//                }
//            });
//        return result;
        }

    /**
     * Attempt to commit a fully prepared transaction.
     *
     * @param rec  the TxRecord representing the transaction to finish committing
     *
     * @return True iff the Transaction was successfully committed
     */
    protected Boolean commit(TxRecord rec)
        {
        TODO
        }

    /**
     * Attempt to roll back an in-flight transaction.
     *
     * @param rec  the TxRecord representing the transaction to roll back
     *
     * @return True iff the Transaction was rolled back
     */
    protected Boolean rollback(TxRecord rec)
        {
        Int              writeId = rec.writeId;
        Set<ObjectStore> stores  = rec.enlisted;
        if (stores.empty)
            {
            rec.status = RolledBack;
            byWriteId.remove(writeId);
            byClientId.remove(rec.clientId);
            return True;
            }

        FutureVar<Tuple<>>? rollbackAll = Null;
        rec.status = RollingBack;
        for (ObjectStore store : stores)
            {
            @Future Tuple<> rollbackOne = store.rollback(writeId);
            rollbackAll = rollbackAll?.and(&rollbackOne, (_, _) -> Tuple:()) : &rollbackOne;
            }

        assert rollbackAll != Null;
        @Future Boolean completed = rollbackAll.transform((Tuple<> t) ->
            {
            byWriteId.remove(writeId);
            byClientId.remove(rec.clientId);
            rec.enlisted = [];
            rec.status   = RolledBack;
            return True;
            });
        return completed;
        }

    /**
     * Finish the specified transaction.
     *
     * @param rec     the transaction record
     * @param status  the terminal status of the transaction
     */
    protected void terminate(TxRecord rec, TxRecord.Status status)
        {
        byWriteId.remove(rec.writeId);
        byClientId.remove(rec.clientId);
        rec.status = status;
        }


    // ----- internal: TxRecord --------------------------------------------------------------------

    /**
     * This is the information that the TxManager maintains about each in flight transaction.
     */
    protected static class TxRecord(
            Client.Transaction  clientTx,
            TxInfo              txInfo,
            Int                 clientId,
            Int                 writeId,
            Int                 readId    = NO_TX,
            Int                 prepareId = NO_TX,
            Set<ObjectStore>    enlisted  = [],         // TODO set of ids
            Set<ObjectStore>    triggered = [],         // TODO set of ids
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
         * Mark a transactional resource as being triggered.
         *
         * @param store  the resource to enlist
         */
        void trigger(ObjectStore store)
            {
            if (triggered.is(immutable Object))
                {
                triggered = new HashSet(triggered);
                }

            assert triggered.addIfAbsent(store);
            }

        /**
         * @return the ObjectStores **in a predictable order** that need to have triggers evaluated
         */
        Iterable<ObjectStore> takeTriggered()
            {
            if (triggered.empty)
                {
                return [];
                }

            val result = triggered.sorted((os1, os2) -> os1.id <=> os2.id);
            triggered = [];
            return result;
            }

        /**
         * Transaction status.
         */
        enum Status
            {
            InFlight,
            Preparing,
            Validating,
            Rectifying,
            Distributing,
            Prepared,
            Committing,
            Committed,
            RollingBack,
            RolledBack,
            }
        }
    }
