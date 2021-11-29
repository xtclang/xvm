import collections.ArrayDeque;
import collections.CircularArray;
import collections.SparseIntSet;

import json.ElementOutput;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.ObjectOutputStream;
import json.Parser;
import json.Printer.printString;

import model.DBObjectInfo;
import model.SysInfo;

import oodb.DBClosed;
import oodb.DBObject;
import oodb.RootSchema;
import oodb.Transaction.CommitResult;
import oodb.Transaction.TxInfo;

import storage.JsonNtxCounterStore;
import storage.ObjectStore;

import Catalog.BuiltIn;
import ObjectStore.MergeResult;


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
 *
 * There are three file categories managed by the TxManager:
 *
 * * The `txmgr.json` file records the last known "check point" of the transaction manager, which
 *   is when the transaction manager was last enabled, disabled, or when the transaction log last
 *   rolled (the then-current `txlog.json` log file grew too large, so it was made an archive and a
 *   new one was started). It contains its a record of its then-current status, (size, timestamp,
 *   and transaction range, ending with the then-latest commit id) of the `txlog.json` log file, and
 *   a record of all known log archives, including the file name, size, timestamp, and transaction
 *   range for each.
 *
 * * The `txlog.json` file is the current transaction log. It is an array of entries, most of which
 *   are likely to be transaction entries, plus a few transaction manager-related entries, such
 *   as (i) log created (the first entry in the log), (2) log archived (the last entry in the log),
 *   (3) TxManager enabled, (4) TxManager disabled.
 *
 * * The log archives are copies of the previous `txlog.json` files, from when those files passed a
 *   certain size threshold. They are named `txlog_<datetime>.json`
 *
 * An example of the transaction file format:
 *
 *    append this information to the end of the database tx log, e.g.:
 *        [
 *        {"_op":"created", "_ts":"2021-09-02T22:55:54.257Z", "_prev_v":23},
 *        {"_v":1, "_tx":1, "_ts":"2021-09-02T22:55:54.672Z", "title":"My Contacts"},
 *        {"_v":2, "_tx":3, "_ts":"2021-09-02T22:55:55.272Z", "contacts":[{"k":"Washington, George",
 *          "ver":{"firstName":"George","lastName":"Washington","emails":[],"phones":[]}}]
 *        {"_op":"closed", "_ts":"2021-09-02T22:55:55.272Z"}
 *        ]
 *
 * The use of leading underscores allows the paths of the various DBObjects (e.g. "title",
 * "contacts") to be used as keys, without the potential for conflicting with the keys used by the
 * TxManager itself:
 *
 * * `_v` - the database version id (the "commit id", i.e. an internal transaction id)
 * * `_tx` - the "visible" transaction id from the TxInfo record of the committed transaction
 * * `_name` - the "visible" transaction name from the TxInfo record of the committed transaction
 * * `_ts` - the clock's timestamp
 * * `_rc` - the retry count before the transaction successfully committed
 * * `_op` - operation indicator (for anything not a transaction record)
 * * * `created`
 * * * `opened`
 * * * `recovered`
 * * * `safepoint`
 * * * `closed`
 * * * `archived`
 * * `_prev_v` - the last transaction from the previous transaction log segment (previous file)
 */
@Concurrent
service TxManager<Schema extends RootSchema>(Catalog<Schema> catalog)
        implements Closeable
    {
    construct(Catalog<Schema> catalog)
        {
        this.catalog = catalog;
        this.clock   = catalog.clock;

        // build the quick lookup information for the optional transactional "modifiers"
        // (validators, rectifiers, and distributors)
        Boolean hasValidators    = catalog.metadata?.dbObjectInfos.any(info -> !info.validators   .empty) : False;
        Boolean hasRectifiers    = catalog.metadata?.dbObjectInfos.any(info -> !info.rectifiers   .empty) : False;
        Boolean hasDistributors  = catalog.metadata?.dbObjectInfos.any(info -> !info.distributors .empty) : False;
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
            syncTriggers = new SparseIntSet()
                    .addAll(validators)
                    .addAll(rectifiers)
                    .addAll(distributors);
            }

        internalJsonSchema = catalog.internalJsonSchema;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The clock shared by all of the services in the database.
     */
    Clock clock;

    /**
     * The runtime state of the TxManager:
     * * **Initial** - the TxManager has been newly created, but has not been used yet
     * * **Enabled** - the TxManager has been [enabled](enable), and can process transactions
     * * **Disabled** - the TxManager was previously enabled, but has been [disabled](disable), and
     *   can not process transactions until it is re-enabled
     * * **Closed** - the TxManager has been closed, and may not be used again
     */
    enum Status {Initial, Enabled, Disabled, Closed}

    /**
     * The status of the transaction manager. For the most part, this could be handled with a simple
     * "Boolean enabled" flag, but the two additional book-end states provide more clarity for
     * the terminal points of the life cycle, and thus (in theory) better assertions.
     */
    public/private Status status = Initial;

    /**
     * The lazily cached DBObjectInfo for each user defined DBObject in the `Catalog`.
     */
    private DBObjectInfo?[] infos = new DBObjectInfo?[];

    /**
     * The ObjectStore for each user defined DBObject in the `Catalog`. These provide the I/O for
     * the database.
     *
     * This data structure is lazily populated as necessary from the [Catalog], which is responsible
     * for creating and holding the references to each [ObjectStore].
     */
    private ObjectStore?[] appStores = new ObjectStore?[];

    /**
     * The ObjectStore for each DBObject in the system schema.
     *
     * This data structure is lazily populated as necessary from the [Catalog], which is responsible
     * for creating and holding the references to each [ObjectStore].
     */
    private ObjectStore?[] sysStores = new ObjectStore?[];

    /**
     * The system directory (./sys).
     */
    @Lazy public/private Directory sysDir.calc()
        {
        Directory root = catalog.dir;
        return root.store.dirFor(root.path + "sys");
        }

    /**
     * The transaction log file (sys/txlog.json).
     */
    @Lazy public/private File logFile.calc()
        {
        Directory root = catalog.dir;
        return root.store.fileFor(root.path + "sys" + "txlog.json");
        }

    /**
     * The transaction log file (sys/txlog.json).
     */
    @Lazy public/private File statusFile.calc()
        {
        Directory root = catalog.dir;
        return root.store.fileFor(root.path + "sys" + "txmgr.json");
        }

    /**
     * A snapshot of information about a transactional log file.
     */
    static const LogFileInfo(String name, Range<Int> txIds, Int safepoint, Int size, DateTime timestamp)
        {
        LogFileInfo with(String?     name      = Null,
                         Range<Int>? txIds     = Null,
                         Int?        safepoint = Null,
                         Int?        size      = Null,
                         DateTime?   timestamp = Null)
            {
            return new LogFileInfo(name      ?: this.name,
                                   txIds     ?: this.txIds,
                                   safepoint ?: this.safepoint,
                                   size      ?: this.size,
                                   timestamp ?: this.timestamp);
            }
        }

    /**
     * A JSON Mapping to use to serialize instances of LogFileInfo.
     */
    @Lazy protected Mapping<LogFileInfo> logFileInfoMapping.calc()
        {
        return internalJsonSchema.ensureMapping(LogFileInfo);
        }

    /**
     * A JSON Mapping to use to serialize instances of LogFileInfo.
     */
    @Lazy protected Mapping<LogFileInfo[]> logFileInfoArrayMapping.calc()
        {
        return internalJsonSchema.ensureMapping(LogFileInfo[]);
        }

    /**
     * A record of all of the existent rolled-over log files.
     */
    protected/private LogFileInfo[] logInfos = new LogFileInfo[];

    /**
     * The maximum size log to store in any one log file.
     * TODO this setting should be configurable (need a "Prefs" API)
     */
    protected Int maxLogSize = 100K;

    /**
     * Previous modified date/time of the log.
     */
    protected DateTime expectedLogTimestamp = EPOCH;

    /**
     * The JSON Schema to use (for system classes).
     */
    protected/private json.Schema internalJsonSchema;

    /**
     * The storage implementation for the transaction counter used to generate externally visible
     * transaction identities.
     */
    @Lazy
    protected/private JsonNtxCounterStore visibleIdCounter.calc()
        {
        return catalog.storeFor(BuiltIn.TxCounter.id).as(JsonNtxCounterStore);
        }

    /**
     * An illegal transaction ID used to indicate that no ID has been assigned yet.
     */
    static Int NO_TX = Int.minvalue;

    /**
     * The count of transactions (referred to as "write transactions") that this transaction manager
     * has created. This counter is used to provide unique write transaction IDs for each in-flight
     * transaction, and also for any trigger processing that occurs during the prepare phase of a
     * transaction. These values are not stored persistently; they only exist for the duration of an
     * in-flight transaction.
     *
     * When a new transaction manager is instantiated, the counter always starts over at zero,
     * since the count and the ids are ephemeral.
     *
     * An ID is calculated as the negative of the count; for example, the first transaction would
     * increment the count to `1`, giving the transaction a "writeId" of `-1`. The corresponding
     * "readId" is not assigned until an operation within the transaction attempts to perform a
     * read, at which point the transaction manager will provide the `lastPrepared` transaction as
     * the "readId".
     *
     * (Note that the second `writeId` will be `-5`, and not `-2`. The count is incremented by 1
     * step, but writeIds are incremented by 4 steps, because each transaction goes through up to 4
     * states from beginning to fully committed.)
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
    public/private Int lastCommitted = 0;

    /**
     * The last transaction id that is known to have been fully written to disk by all ObjectStores.
     */
    Int safepoint.get()
        {
        Int safepoint = lastCommitted;
        for (TxRecord rec : byWriteId.values)
            {
            if (rec.status == Committed && rec.commitId <= safepoint)
                {
                assert rec.commitId > 0;
                safepoint = rec.commitId - 1;
                }
            }
        return safepoint;
        }

    /**
     * The last recorded safepoint.
     */
    Int previousSafepoint = NO_TX;

    /**
     * The transactions that have begun, but have not yet committed or rolled back. The key is the
     * client id. Each client may have up to one "in flight" transaction at any given time.
     */
    protected/private Map<Int, TxRecord> byClientId = new HashMap();

    /**
     * The transactions that have begun, but have not yet committed or rolled back, keyed by the
     * write transaction id. These are the same transactions as exist in the [byClientId] map, but
     * indexed by writeId instead of by client id.
     */
    protected/private Map<Int, TxRecord> byWriteId = new HashMap();

    /**
     * A **count** of the transactions that have begun, but have not yet committed or rolled back,
     * keyed by the read transaction id. The transaction manager uses this information to determine
     * when a historical read transaction can be discarded by the ObjectStore instances; as long as
     * a read transaction is in use by a write transaction, its information must be retained and
     * kept available to clients.
     */
    protected/private OrderedMap<Int, Int> byReadId = new SkiplistMap();

// TODO CP remove this unless it gets used
//    /**
//     * The oldest transaction id that is still being used.
//     */
//    Int retainTxId.get()
//        {
//        if (Int oldest := byReadId.first())
//            {
//            return oldest;
//            }
//
//        return lastCommitted;
//        }

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
     * A pool of artificial clients for processing triggers (validator/rectifier/distributor).
     */
    protected/private ArrayDeque<Client<Schema>> clientCache = new ArrayDeque();

    /**
     * Information about a [DBObject.require] call during a transaction.
     */
    static const Requirement<Result extends immutable Const>
        (
        Int                       dboId,
        function Result(DBObject) test,
        Result                    result,
        );

    /**
     * The writeId of the transaction, if any, that is currently preparing.
     */
    protected/private Int currentlyPreparing = NO_TX;

    /**
     * The count of transactions, if any, that are in the process of terminating after a request
     * has been made to disable the TxManager.
     */
    protected/private Int remainingTerminating = 0;

    /**
     * A queue of transactions that are waiting for the "green light" to start preparing. While this
     * could be easily implemented using just a critical section, it would dramatically impinge on
     * the concurrent execution of transaction processing, particularly since only a small portion
     * of the prepare work is done by the TxManager service -- most is delegated to the various
     * enlisted ObjectStore instances, or onto Client objects that handle the potentially expensive
     * tasks of transaction validation, rectification, and distribution. As a result, the prepare
     * process is staggeringly asynchronous, and many other things -- except other prepares! -- can
     * be occurring while a transaction is being prepared. The result of this design is that any
     * transaction that shows up to be prepared while another transaction is already being prepared,
     * creates a future and places it into the queue for processing once all of the transactions
     * ahead of it have prepared.
     */
    protected/private CircularArray<TxRecord> pendingPrepare = new CircularArray();


    // ----- lifecycle management ------------------------------------------------------------------

    /**
     * Allow the transaction manager to accept work from clients.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @param recover  pass true to enable database recovery, if recovery is necessary
     *
     * @return True iff the TxManager was successfully enabled
     */
    Boolean enable(Boolean recover=False)
        {
        switch (status)
            {
            case Initial:
                using (new SynchronizedSection())
                    {
                    if ((statusFile.exists ? openLog() : createLog()) || recover && recoverLog())
                        {
                        status = Enabled;
                        return True;
                        }
                    }
                return False;

            case Enabled:
                return True;

            case Disabled:
                using (new SynchronizedSection())
                    {
                    if (remainingTerminating == 0 && (openLog() || recover && recoverLog()))
                        {
                        status = Enabled;
                        return True;
                        }
                    }
                return False;

            case Closed:
                return False;
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
        return status == Enabled
            ? True
            : throw new DBClosed($"TxManager is not enabled (status={status})");
        }

    /**
     * Stop the transaction manager from accepting any work from clients, and roll back any
     * transactions that have not already prepared.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @param abort  pass True to abort everything that can still be aborted in order to disable as
     *               quickly as possible with minimal risk of failure
     *
     * @return True iff the TxManager was successfully disabled
     */
    Boolean disable(Boolean abort = False)
        {
        switch (status)
            {
            case Initial:
                status = Disabled;
                return True;

            case Enabled:
                using (new SynchronizedSection())
                    {
                    @Future Boolean result;

                    // count down the remaining number of transactions to clean up, but start with
                    // an extra one, to prevent the shut-down from "completing" while we're still
                    // inside the loop below
                    assert remainingTerminating == 0;
                    remainingTerminating = 1;

                    SkiplistMap<Int, TxRecord> commitOrder    = new SkiplistMap();
                    Int                        lastCommitted  = this.lastCommitted;
                    TxRecord?                  lastCommitting = Null;

                    Loop: for (TxRecord rec : byWriteId.values)
                        {
                        Boolean doCommit   = False;
                        Boolean doRollback = False;
                        switch (rec.status)
                            {
                            default:
                            case InFlight:
                            case Enqueued:
                            case Preparing:
                            case Prepared:
                            case ReqsChecking:
                            case ReqsChecked:
                            case Validating:
                            case Validated:
                            case Rectifying:
                            case Rectified:
                            case Distributing:
                            case Distributed:
                            case Sealing:
                                doRollback = True;
                                break;

                            case Sealed:
                                if (abort)
                                    {
                                    doRollback = True;
                                    }
                                else
                                    {
                                    doCommit = True;
                                    }
                                break;

                            case Committing:
                                // remember the last committing transaction
                                if (rec.prepareId > lastCommitted)
                                    {
                                    lastCommitted  = rec.prepareId;
                                    lastCommitting = rec;
                                    }
                                // already in progress to being "done"; no means to front-run it, so
                                // it must be allowed to complete
                                break;

                            case RollingBack:
                                // already in progress to being "done"; no means to front-run it, so
                                // it must be allowed to complete
                                break;

                            case Committed:
                            case RolledBack:
                                // already "done"
                                continue Loop;
                            }

                        ++remainingTerminating;

                        rec.addTermination(() ->
                            {
                            if (finalTermination())
                                {
                                // this is the last transaction to finish terminating; finish
                                // the disable() call by completing its future result
                                result = True;
                                }
                            });

                        if (doCommit)
                            {
                            assert rec.prepareId != NO_TX && commitOrder.putIfAbsent(rec.prepareId, rec);
                            }

                        if (doRollback)
                            {
                            rec.rollback(RollbackOnly);
                            }
                        }

                    if (!commitOrder.empty)
                        {
                        TxRecord[] commitRecs = commitOrder.values.toArray();
                        if (lastCommitting?.status == Committing)
                            {
                            lastCommitting.addTermination(() -> commit(commitRecs));
                            }
                        else
                            {
                            commit(commitRecs);
                            }
                        }

                    closeLog();
                    status = Disabled;

                    if (finalTermination())
                        {
                        // either there was nothing to close down, or it already finished before we
                        // could return the future that would close the log; either way, the future
                        // is now
                        result = True;
                        }

                    // still some futures that need to finish before the log gets closed
                    return result;
                    }

            case Disabled:
            case Closed:
                return True;
            }
        }

    /**
     * Check the transaction wind-down process and reset the transaction manager state after it has
     * been fully disabled.
     *
     * @return True once there are no more transactions to terminate
     */
    protected Boolean finalTermination()
        {
        assert remainingTerminating >= 1;

        Boolean last = remainingTerminating == 1;
        if (last)
            {
            // the transactions have already terminated (or there were none to
            // terminate), so the "shut down" is done; reset the TxManager
            assert status == Disabled;
            logInfos.clear();
            byClientId.clear();
            byWriteId.clear();
            byReadId.clear();
            pendingPrepare.clear();
            currentlyPreparing = NO_TX;
            }

        // only after having done the reset (on the last one) do we decrement the remaining count,
        // because the remaining count prevents enable() from accidentally proceeding; in other
        // words, when status==Disabled and remainingTerminating>0, the actual status is "almost
        // disabled" i.e. "Disabling" but not yet completely "Disabled"
        --remainingTerminating;

        return last;
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
     * @return the next transaction id to use; this is the transaction id that will be visible
     *         through the oodb API, and not related to either the `readId` or `writeId` used
     *         internally by this jsonDB implementation
     */
    UInt generateTxId()
        {
        return visibleIdCounter.next().toUInt64();
        }

    /**
     * Begin a new transaction when it performs its first operation.
     *
     * This method is intended to only be used by the [Client].
     *
     * The transaction manager assigns a "write" temporary transaction id that the transaction will
     * use for all of its mutating operations (the "write TxId"). A "read" transaction id is
     * assigned the first time that the transaction attempts to read data. The read TxId ensures
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
     * @param worker    the Client Worker service to offload expensive CPU tasks onto
     * @param readOnly  if the transaction is not allowed to make any changes
     *
     * @return  the "write" transaction ID to use
     */
    Int begin(Client.Transaction tx, Client.Worker worker, Boolean readOnly)
        {
        checkEnabled();

        Int clientId = tx.outer.id;
        assert !byClientId.contains(clientId);

        Int      writeId = genWriteId();
        TxRecord rec     = new TxRecord(tx, tx.txInfo, clientId, worker, writeId, readOnly);

        byClientId.put(clientId, rec);
        byWriteId .put(writeId , rec);

        return writeId;
        }

    /**
     * Register the specified requirement for the specified transaction.
     *
     * @param writeId  the transaction id
     * @param req      the Requirement
     */
    <Result extends immutable Const> Result registerRequirement(Int writeId, Int dboId, function Result(DBObject) test)
        {
        checkEnabled();

        // validate the transaction
        TxRecord rec = txFor(writeId);
        assert rec.status == InFlight;

        Client client = rec.ensureClient(ReadOnly);
        Result result;
        try
            {
            result = client.evaluateRequirement(dboId, test);
            }
        finally
            {
            rec.releaseClient();
            }

        rec.registerRequirement(new Requirement(dboId, test, result));
        return result;
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
    CommitResult commit(Int writeId)
        {
        checkEnabled();

        // validate the transaction
        TxRecord rec = txFor(writeId);
        assert rec.status == InFlight;

        // we don't have a result yet, but there will be a result (either the transaction is about
        // to be processed, or it is queued), so use a "future result" to represent the
        // result of the commit
        @Future CommitResult result;
        rec.pending = &result;
        rec.status  = Enqueued;

        Boolean occupied   = currentlyPreparing != NO_TX;
        Boolean backlogged = !pendingPrepare.empty;

        // if there is already a transaction in the prepare phase, then get in line
        if (occupied || backlogged)
            {
            // stick the transaction into the queue of transactions to prepare
            pendingPrepare.add(rec);

            // if there's a prepare backlog, but no fiber already processing it, then this
            // fiber takes responsibility for processing the entire line up to and including the
            // transaction (writeId) that this method was called to process; this may seem "unfair",
            // but since all of those transactions have to prepare before this one can, it makes no
            // sense (i.e. it is inherently less efficient) to ask someone else to do the work.
            // otherwise, if there's a transaction that is already in the process of preparing, then
            // that transaction is responsible for creating a fiber to work through the backlog; our
            // job here is done (even though we haven't actually done anything, other than
            // registering the fact that this transaction needs to be committed); instead, we
            // simply return the future result to the client, and trust that it will get processed
            // as soon as the TxManager can get to it
            if (!occupied)
                {
                processBacklog(stopAfterId=writeId);
                }

            return result;
            }

        currentlyPreparing = writeId;   // prevent concurrent transaction prepare

        // a simple function to evaluate the next step, and report an error if the step fails
        static Future<Boolean> eval(Future<Boolean>      previousResult,
                                    function Boolean()   nextStep,
                                    CommitResult         stepFailed,
                                    Future<CommitResult> result)
            {
            return previousResult.transform(ok ->
                {
                if (ok)
                    {
                    if (nextStep())
                        {
                        // proceed
                        return True;
                        }

                    // this step killed the pipeline; deliver the result back to the client
                    if (!result.assigned)
                        {
                        result.complete(stepFailed);
                        }
                    }

                return False;
                });
            }

        @Future Boolean start; // we just need some Future Boolean to chain from
        FutureVar<Boolean> proceed = eval(&start, rec.prepare, ConcurrentConflict, &result);
        &start.complete(True); // kick off the prepare

        // "require" the transaction
        if (!rec.requirements.empty)
            {
            proceed = eval(proceed, rec.require, ConcurrentConflict, &result);
            }

        // "validate" the transaction
        if (!validators.empty)
            {
            proceed = eval(proceed, rec.validate, ValidatorFailed, &result);
            }

        // "rectify" the transaction
        if (!rectifiers.empty)
            {
            proceed = eval(proceed, rec.rectify, RectifierFailed, &result);
            }

        // "distribute" the transaction
        if (!distributors.empty)
            {
            proceed = eval(proceed, rec.distribute, DistributorFailed, &result);
            }

        // "seal" the transaction
        proceed = eval(proceed, rec.seal, DatabaseError, &result);

        // handle any exception that occurred in any of the above steps (implying a failure)
        proceed = proceed.handle(e ->
                {
                log($"Exception occurred while preparing transaction {rec.idString}: {e}");
                if (!&result.assigned)
                    {
                    result = DatabaseError;
                    }
                return False;
                });

        // this is the end of the line (even though it is still far in the future, and won't be
        // executed until all of the other futures above get evaluated); our job here is to either
        // do the final commit or roll back of the transaction
        proceed = proceed.transform(ok ->
            {
            // whether or not the prepare succeeded, it is done and we need to allow another
            // transaction to prepare
            currentlyPreparing = NO_TX;

            // check if any transactions showed up while this transaction was preparing, and start
            // a new fiber to process the backlog, if one exists
            if (!pendingPrepare.empty && status == Enabled)
                {
                this:service.callLater(processBacklog);
                }

            // if everything thus far has succeeded, then we attempt to commit the transaction
            if (rec.status == Sealed)
                {
                try
                    {
                    Boolean success = rec.commit();
                    if (!&result.assigned)
                        {
                        result = success ? Committed : DatabaseError;
                        }
                    return success;
                    }
                catch (Exception e)
                    {
                    log($"Exception occurred while committing transaction {rec.idString}: {e}");
                    }
                }

            // if we didn't successfully commit (for any reason), then we roll back
            try
                {
                rec.rollback();
                }
            catch (Exception e)
                {
                log($|Exception occurred while rolling back transaction {rec.idString}
                     | after it failed to {ok ? "commit" : "prepare"}: {e}
                    );
                }

            Boolean success = rec.status == Committed;
            if (!&result.assigned)
                {
                result = success ? Committed : DatabaseError;
                }
            return success;
            });

        return result;
        }

    /**
     * This method processes all of the transactions that have queued to prepare.
     *
     * @param stopAfterId  (optional)
     */
    protected void processBacklog(Int? stopAfterId = Null)
        {
        if (currentlyPreparing != NO_TX)
            {
            // excellent! another fiber is already processing the backlog
            return;
            }

        // transactions that are have prepared but have not yet been committed to disk (or that
        // failed to prepare and need to be rolled back)
        TxRecord[] pendingCommit   = new TxRecord[];
        TxRecord[] pendingRollback = new TxRecord[];

        while (TxRecord rec := pendingPrepare.first())
            {
            Int writeId = rec.writeId;
            currentlyPreparing = rec.writeId;
            pendingPrepare.delete(0);

            if (rec.status != Enqueued)
                {
                // some other fiber has already done something with the transaction
                continue;
                }

            Boolean successfullyPrepared = False;
            try
                {
                successfullyPrepared = rec.prepare()
                        && (validators  .empty || rec.validate  ())
                        && (rectifiers  .empty || rec.rectify   ())
                        && (distributors.empty || rec.distribute())
                        && rec.seal();
                }
            catch (Exception e)
                {
                log($"Exception occurred while preparing transaction {rec.idString}: {e}");
                }

            (successfullyPrepared ? pendingCommit : pendingRollback).add(rec);

            // the backlog processing may be limited, such that it only processes up to a specified
            // transaction
            if (writeId == stopAfterId)
                {
                break;
                }
            }

        // release the "prepare pipeline" so another fiber can do prepares
        currentlyPreparing = NO_TX;

        // if this was only processing up to "stopAfterId", then more transactions may have shown
        // up since then, and they need to be processed (but not by this fiber)
        if (stopAfterId != Null && !pendingPrepare.empty)
            {
            this:service.callLater(processBacklog);
            }

        // first, process the important stuff (the pending commits)
        if (!pendingCommit.empty)
            {
            try
                {
                if (!commit(pendingCommit))
                    {
                    log($"Unable to successfully commit a batch of transactions: {pendingCommit}");
                    panic();
                    }
                }
            catch (Exception e)
                {
                log($"An error occurred committing a batch of transactions: {e}");
                panic();
                }
            }

        // lastly, process the pending rollbacks
        for (TxRecord rec : pendingRollback)
            {
            rec.rollback();
            }
        }

    /**
     * Attempt to commit an array of fully prepared transactions.
     *
     * @param recs  the TxRecords representing the transactions to finish committing
     *
     * @return True iff all of the Transactions were successfully committed
     */
    protected Boolean commit(TxRecord[] recs)
        {
        Boolean success = True;

        // bundle the results of "sealPrepare()" into a buffer
        StringBuffer buf       = new StringBuffer();
        Int          lastAdded = NO_TX;
        TxRecord[]   processed = new TxRecord[];
        NextTx: for (TxRecord rec : recs)
            {
            switch (rec.status)
                {
                case Sealed:
                    assert rec.prepareId > lastAdded;
                    rec.status = Committing;
                    break;

                case Committing:
                case Committed:
                    // assume that another fiber had a reason to commit the transaction
                    continue NextTx;

                case RollingBack:
                case RolledBack:
                    // assume that another fiber had a reason to roll back the transaction
                    success = False;
                    continue NextTx;

                default:
                    throw new IllegalState($"Unexpected status for transaction {rec.idString}: {rec.status}");
                }

            rec.addSeal(buf);
            lastAdded  = rec.prepareId;
            processed += rec;
            }

        if (processed.empty)
            {
            return success;
            }

        // append all of the commits to the log (this officially "commits" the transactions)
        buf.append("\n]");
        appendLog(buf.toString());
        assert lastCommitted < lastAdded;
        lastCommitted = lastAdded;

        if (logFile.size > maxLogSize)
            {
            rotateLog();
            }

        // notify the clients that their transactions have committed
        for (TxRecord rec : processed)
            {
            rec.complete(Committed);
            }

        // direct the ObjectStores to write (i.e. asynchronously catch up to the transaction log),
        // and clean up the transaction records
        @Future Boolean initialResult = success;
        Future<Boolean> finalResult   = &initialResult;
        for (TxRecord rec : processed)
            {
            Set<Int> storeIds = rec.enlisted;
            if (storeIds.empty)
                {
                continue;
                }

            Future<Tuple<>>? storeAll = Null;
            Int              writeId  = rec.writeId;
            for (Int storeId : storeIds)
                {
                @Future Tuple<> storeOne = storeFor(storeId).commit(writeId);
                storeAll = storeAll?.and(&storeOne, (t, _) -> t) : &storeOne; // TODO GG wide ass -> Tuple:()
                }
            assert storeAll != Null;

            Future<Boolean> incrementalResult = storeAll.transformOrHandle((t, e) ->
                {
                Boolean localSuccess = True;

                if (e != Null)
                    {
                    log($"HeuristicException during commit of {rec} caused by: {e}");
                    // REVIEW should this panic() right here?
                    localSuccess = False;
                    }

                // the existence of this transaction prevents the safepoint from advancing, so once
                // it completes, it needs to be removed
                rec.release();

                return localSuccess;
                });

            finalResult = finalResult.and(incrementalResult, (ok1, ok2) -> ok1 & ok2);
            }

        return finalResult;
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
    Boolean rollback(Int writeId)
        {
        // validate the transaction
        if (writeId == NO_TX || txCat(writeId) == ReadOnly)
            {
            // nothing to roll back, so consider it a success
            return True;
            }

        checkEnabled();

        TxRecord rec = txFor(writeId);
        assert rec.status == InFlight;
        return rec.rollback();
        }


    // ----- transactional ID helpers --------------------------------------------------------------

    /**
    * Obtain the readId for the specified transaction.
    *
    * @param writeId  the transaction id (the "writeId")
    *
    * @return the corresponding readId
    */
    Int readIdFor(Int txId)
        {
        return txFor(txId).readId;
        }

    /**
    * Obtain the `Open` (not `Validating`, `Rectifying`, or `Distributing`) writeId for the
    * specified transaction ID.
    *
    * @param txId  the transaction id (in the "writeId" range)
    *
    * @return the corresponding writeId
    */
    static Int writeIdFor(Int txId)
        {
        assert isWriteTx(txId);
        return -(-txId & ~0b11);
        }

    /**
     * Determine if the specified txId indicates a read ID, versus a write ID.
     *
     * @param txId  a transaction identity
     *
     * @return True iff the txId is in the range reserved for read transaction IDs
     */
    static Boolean isReadTx(Int txId)
        {
        return txId >= 0;
        }

    /**
     * Determine if the specified txId indicates a write ID, versus a read ID.
     *
     * @param txId  a transaction identity
     *
     * @return True iff the txId is in the range reserved for write transaction IDs
     */
    static Boolean isWriteTx(Int txId)
        {
        return NO_TX < txId < 0;
        }

    /**
     * Categories of transaction IDs.
     */
    enum TxCat {ReadOnly, Open, Validating, Rectifying, Distributing}

    /**
     * Determine the category of the transaction ID.
     *
     * @param txId  any transaction ID
     *
     * @return the TxCat for the specified transaction ID
     */
    static TxCat txCat(Int txId)
        {
        return txId >= 0
                ? ReadOnly
                : TxCat.values[1 + (-txId & 0b11)];
        }

    /**
     * Obtain the transaction counter that was used to create the specified writeId.
     *
     * @param writeId  a transaction ID of type `WriteId`, `Validating`, `Rectifying`, or
     *                 `Distributing`
     *
     * @return the original transaction counter
     */
    protected static Int writeTxCounter(Int writeId)
        {
        assert isWriteTx(writeId);
        return -writeId >>> 2;
        }

    /**
     * Given a transaction counter, produce a transaction ID of type `WriteId`.
     *
     * @param counter  the transaction counter
     *
     * @return the writeId for the transaction counter
     */
    protected static Int generateWriteId(Int counter)
        {
        assert counter >= 1;
        return -(counter<<2);
        }

    /**
     * Given a transaction `writeId`, generate a transaction id for the specified phase
     */
    protected static Int generateTxId(Int txId, TxCat txCat)
        {
        if (isReadTx(txId))
            {
            assert txCat == ReadOnly;
            return txId;
            }

        assert isWriteTx(txId);
        return -((-txId & ~0b11) | txCat.ordinal - 1);
        }

    /**
     * Generate a write transaction ID.
     *
     * @return the new write id
     */
    protected Int genWriteId()
        {
        return generateWriteId(++txCount);
        }


    // ----- internal: TxRecord --------------------------------------------------------------------

    /**
     * Look up the specified TxRecord.
     *
     * @param txId  the transaction writeId
     *
     * @return the TxRecord for the transaction
     */
    TxRecord txFor(Int txId)
        {
        return txCat(txId) == ReadOnly
                ? assert as $"Transaction {txId} is a ReadOnly transaction"
                : byWriteId[writeIdFor(txId)] ?: assert as $"Missing TxRecord for txId={txId}";
        }

    /**
     * This is the information that the TxManager maintains about each in flight transaction.
     */
    @Concurrent protected class TxRecord
        {
        /**
         * Construct a TxRecord.
         */
        construct(Client.Transaction  clientTx,
                  TxInfo              txInfo,
                  Int                 clientId,
                  Client.Worker       worker,
                  Int                 writeId,
                  Boolean             readOnly,
                 )
            {
            this.clientTx = clientTx;
            this.txInfo   = txInfo;
            this.clientId = clientId;
            this.worker   = worker;
            this.writeId  = writeId;
            this.readId   = NO_TX;
            this.readOnly = readOnly;
            }

        /**
         * Transaction status.
         *
         * * InFlight - the transaction is "open", and still accepting client operations
         * * Enqueued - the transaction is beginning to prepare, or waiting for its turn to prepare
         * * Preparing - the transaction has begun the "prepare" step
         * * Prepared - the transaction has completed the "prepare" step
         * * ReqsChecking - the transaction has begun checking the requirements
         * * ReqsChecked - the transaction has completed checking the requirements
         * * Validating - the transaction has begun the "validate" step
         * * Validated - the transaction has completed the "validate" step
         * * Rectifying - the transaction has begun the "rectify" step
         * * Rectified - the transaction has completed the "rectify" step
         * * Distributing - the transaction has begun the "distribute" step
         * * Distributed - the transaction has completed the "distribute" step
         * * Committing - the transaction has begun the final commit process
         * * Committed - the transaction has successfully committed
         * * RollingBack - the transaction has begun the rollback process
         * * RolledBack - the transaction has successfully rolled back
         */
        enum Status
            {
            InFlight,
            Enqueued,
            Preparing,
            Prepared,
            ReqsChecking,
            ReqsChecked,
            Validating,
            Validated,
            Rectifying,
            Rectified,
            Distributing,
            Distributed,
            Sealing,
            Sealed,
            Committing,
            Committed,
            RollingBack,
            RolledBack,
            }

        /**
         * The transactional status.
         */
        Status status = InFlight;

        /**
         * The actual Client Transaction object that "fronts" this transaction.
         */
        Client.Transaction clientTx;

        /**
         * The TxInfo record for the transaction.
         */
        TxInfo txInfo;

        /**
         * The internal id that uniquely identifies the client.
         */
        Int clientId;

        /**
         * The Client-provided Worker service, to prevent consuming either the TxManager cycles or
         * cycles of the various ObjectStore.
         */
        Client.Worker worker;

        /**
         * The transaction id while it is in-flight, also called its "writeId".
         */
        Int writeId;

        /**
         * The underlying "read" transaction ID.
         */
        Int readId
            {
            @Override
            void set(Int newId)
                {
                Int oldId = get();
                if (newId != oldId)
                    {
                    if (oldId != NO_TX)
                        {
                        byReadId.process(oldId, entry ->
                            {
                            if (entry.exists && --entry.value <= 0)
                                {
                                entry.delete();
                                }
                            return True;
                            });
                        }

                    if (newId != NO_TX)
                        {
                        byReadId.process(newId, entry ->
                            {
                            if (entry.exists)
                                {
                                ++entry.value;
                                }
                            else
                                {
                                entry.value = 1;
                                }
                            return True;
                            });
                        }

                    super(newId);
                    }
                }
            }

        /**
         * Generate the next "prepare" transaction ID. This alters the transaction's read-id, by
         * "sliding it up" to the latest prepare.
         *
         * @return the new prepare id
         */
        Int selectPrepareId()
            {
            readId = lastPrepared;
            return prepareId;
            }

        /**
         * The ID that the transaction is planning to commit to.
         */
        Int prepareId.get()
            {
            switch (status)
                {
                case Preparing:
                case Prepared:
                case ReqsChecking:
                case ReqsChecked:
                case Validating:
                case Validated:
                case Rectifying:
                case Rectified:
                case Distributing:
                case Distributed:
                case Sealing:
                    // prepare is re-homed "on top of" the previously committed (or previously
                    // prepared) transaction
                    return readId + 1;

                case Sealed:
                case Committing:
                case Committed:
                    assert commitId >= 0 as $"Illegal commitId {commitId}, status={status}";
                    return commitId;

                case InFlight:
                case Enqueued:
                case RollingBack:
                case RolledBack:
                default:
                    return NO_TX;
                }
            }

        /**
         * The permanent id assigned to a committed transaction. This property does not have a real
         * value until the transaction is sealed, which occurs immediately before the commit.
         */
        Int commitId = NO_TX;

        /**
         * All of the require() calls that have occurred within this transaction.
         */
        Requirement[] requirements = [];

        /**
         * Add a requirement to the list of requirements that have occurred within this transaction.
         */
        void registerRequirement(Requirement req)
            {
            if (requirements.empty)
                {
                requirements = new Requirement[];
                }
            requirements.add(req);
            }

        /**
         * This is the Client object that is used to do all of the validate/rectify/distribute work.
         */
        Client<Schema>? prepareClient = Null;

        /**
         * Obtain a Client to handle the specified phase of the prepare process.
         *
         * @param txCat  one of Validating, Rectifying, Distributing
         *
         * @return the client to use
         */
        Client<Schema> ensureClient(TxCat txCat)
            {
            assert txCat != Open;

            Client<Schema>? client = prepareClient;
            if (client == Null)
                {
                client = allocateClient();
                prepareClient = client;
                }

            client.representTransaction(txCat == ReadOnly ? readId : generateTxId(writeId, txCat));
            return client;
            }

        /**
         * Release the previously obtained Client, if any.
         */
        void releaseClient()
            {
            if (Client<Schema> client ?= prepareClient)
                {
                prepareClient = Null;
                client.stopRepresentingTransaction();
                recycleClient(client);
                }
            }

        /**
         * The "seal" for each modified ObjectStore, keyed by ObjectStore id.
         */
        Map<Int, String?> sealById = Map:[];

        /**
         * During prepare processing, this is used to hold the store IDs that enlist during a step.
         */
        Set<Int> newlyEnlisted = [];

        /**
         * The future result for a pending operation on the transaction, if there is a pending
         * operation.
         */
        Future<CommitResult>? pending = Null;

        /**
         * Notification function to call when the transaction terminates.
         */
        function void()? terminated = Null;

        /**
         * Enlist a transactional resource.
         *
         * @param store  the resource to enlist
         */
        void enlist(Int storeId)
            {
            switch (status)
                {
                case Validating:
                case Rectifying:
                case Distributing:
                    if (newlyEnlisted.empty)
                        {
                        newlyEnlisted = new SparseIntSet();
                        }
                    newlyEnlisted.add(storeId);
                    continue;
                case InFlight:
                    if (sealById.empty && sealById.is(immutable Object))
                        {
                        sealById = new SkiplistMap();
                        }
                    assert sealById.putIfAbsent(storeId, Null);
                    break;

                default:
                    assert as $"Attempt to enlist ObjectStore ID {storeId} into a {status} transaction";
                }
            }

        /**
         * The set of enlisted stores.
         */
        Set<Int> enlisted.get()
            {
            return sealById.keys;
            }

        /**
         * @return the ObjectStore ids **in a predictable order** that were enlisted in one of the
         *         Validating/Rectifying/Distributing phases, and since the last call to this method
         */
        Set<Int> takeNewlyEnlisted()
            {
            Set<Int> result = newlyEnlisted;
            newlyEnlisted = [];
            return result;
            }

        /**
         * A string describing the transaction's identity.
         */
        String idString.get()
            {
            return $"{writeId} ({txInfo} for client {clientId})";
            }

        /**
         * True if the transaction is known to be read-only.
         */
        Boolean readOnly;

        /**
         * Attempt to prepare the transaction.
         *
         * @return True iff the Transaction was successfully prepared
         */
        protected Boolean prepare()
            {
            checkEnabled();
            if (status != Enqueued || currentlyPreparing != writeId)
                {
                return status == Committed;
                }

            status = Preparing;

            Set<Int> storeIds = enlisted;
            if (storeIds.empty)
                {
                // an empty transaction is considered committed
                complete(Committed);
                return True;
                }

            // prepare phase
            Boolean             abort       = False;
            FutureVar<Boolean>? preparedAll = Null;

            Int destinationId = selectPrepareId();
            for (Int storeId : storeIds)
                {
                ObjectStore store = storeFor(storeId);

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
                PrepareResult   prepareResult = store.prepare^(writeId, destinationId);
                Future<Boolean> preparedOne   = &prepareResult.transform(result ->
                    {
                    switch (result)
                        {
                        case FailedRolledBack:
                            storeIds.remove(storeId);
                            abort = True;
                            return False;

                        case CommittedNoChanges:
                            storeIds.remove(storeId);
                            return True;

                        case Prepared:
                            return True;
                        }
                    });

                preparedAll = preparedAll?.and(preparedOne, (ok1, ok2) -> ok1 & ok2) : preparedOne;
                }

            assert preparedAll != Null;

            // handle any exception that occurred in the ObjectStore prepare steps
            return preparedAll.handle(e ->
                    {
                    log($"Exception occurred while preparing transaction {this.TxRecord.idString}: {e}");
                    return False;
                    })
                .transform(ok ->
                    {
                    // check for successful prepare, and whether anything is left enlisted
                    switch (ok, !storeIds.empty)
                        {
                        case (False, False):
                            // failed; already rolled back
                            complete(ConcurrentConflict);
                            return False;

                        default:
                        case (False, True):
                            // failed; remaining stores need to be rolled back
                            rollback(ConcurrentConflict);
                            return False;

                        case (True, False):
                            // succeeded; already committed
                            complete(Committed);
                            release();
                            return True;

                        case (True, True):
                            status = Prepared;
                            return True;
                        }
                    });
            }

        /**
         * Evaluate all of the requirements that have been registered for this transaction.
         *
         * @return True iff the Transaction requirements were all met
         */
        protected Boolean require()
            {
            switch (status)
                {
                case Prepared:
                    checkEnabled();
                    assert currentlyPreparing == writeId && !enlisted.empty;
                    status = ReqsChecking;
                    break;

                case Committed:
                    // assume that the transaction already short-circuited to a committed state
                    assert enlisted.empty;
                    return True;

                case RollingBack:
                case RolledBack:
                    // assume that another fiber had a reason to roll back the transaction
                    return False;

                default:
                    throw new IllegalState($"Unexpected status for transaction {idString}: {status}");
                }

            // recheck any requirements on a fake client using the most recently committed data
            if (!requirements.empty)
                {
                Client<Schema> client = ensureClient(ReadOnly);
                for (Requirement req : requirements)
                    {
                    req.Result oldVal = req.result;
                    req.Result newVal = client.evaluateRequirement(req.dboId, req.test);
                    if (oldVal != newVal)
                        {
                        return False;
                        }
                    }
                }

            status = ReqsChecked;
            return True;
            }

        /**
         * Evaluate all of the validators that have been triggered by this transaction.
         *
         * @return True iff the Transaction was successfully validated
         */
        protected Boolean validate()
            {
            switch (status)
                {
                case Prepared:
                case ReqsChecked:
                    checkEnabled();
                    assert currentlyPreparing == writeId && !enlisted.empty;
                    status = Validating;
                    break;

                case Committed:
                    // assume that the transaction already short-circuited to a committed state
                    assert enlisted.empty;
                    return True;

                case RollingBack:
                case RolledBack:
                    // assume that another fiber had a reason to roll back the transaction
                    return False;

                default:
                    throw new IllegalState($"Unexpected status for transaction {idString}: {status}");
                }

            if (validators.empty)
                {
                status = Validated;
                return True;
                }

            Client<Schema> client = ensureClient(Validating);
            for (Int storeId : enlisted)
                {
                if (validators.contains(storeId) && !client.validateDBObject(storeId))
                    {
                    return False;
                    }
                }

            // sweep any newly enlisted stores (no changes were permitted in this phase)
            for (Int storeId : takeNewlyEnlisted())
                {
                // changes are not permitted to occur during the validate phase
                assert storeFor(storeId).mergePrepare(writeId, prepareId) == CommittedNoChanges;
                sealById.remove(storeId);
                }

            status = Validated;
            return True;
            }

        /**
         * Evaluate all of the rectifiers that have been triggered by this transaction.
         *
         * @return True iff the Transaction was successfully rectified
         */
        protected Boolean rectify()
            {
            switch (status)
                {
                case Prepared:
                case ReqsChecked:
                case Validated:
                    checkEnabled();
                    assert currentlyPreparing == writeId;
                    status = Rectifying;
                    break;

                case Committed:
                    // assume that the transaction already short-circuited to a committed state
                    assert enlisted.empty;
                    return True;

                case RollingBack:
                case RolledBack:
                    // assume that another fiber had a reason to roll back the transaction
                    return False;

                default:
                    throw new IllegalState($"Unexpected status for transaction {idString}: {status}");
                }

            if (rectifiers.empty)
                {
                status = Rectified;
                return True;
                }

            Client<Schema> client = ensureClient(Rectifying);
            for (Int storeId : enlisted)
                {
                if (rectifiers.contains(storeId) && !client.rectifyDBObject(storeId))
                    {
                    return False;
                    }
                }

            // sweep any newly enlisted stores (changes may have occurred in this phase)
            for (Int storeId : takeNewlyEnlisted())
                {
                ObjectStore store  = storeFor(storeId);
                MergeResult result = store.mergePrepare(writeId, prepareId);
                switch (result)
                    {
                    case CommittedNoChanges:
                        sealById.remove(storeId);
                        break;

                    case NoMerge:
                    case Merged:
                        sealById.put(storeId, store.sealPrepare(writeId));
                        break;
                    }
                }

            status = Rectified;
            return True;
            }

        /**
         * Evaluate all of the distributors that have been triggered by this transaction.
         *
         * @return True iff the Transaction was successfully distributed
         */
        protected Boolean distribute()
            {
            switch (status)
                {
                case Prepared:
                case ReqsChecked:
                case Validated:
                case Rectified:
                    checkEnabled();
                    assert currentlyPreparing == writeId;
                    status = Distributing;
                    break;

                case Committed:
                    // assume that the transaction already short-circuited to a committed state
                    assert enlisted.empty;
                    return True;

                case RollingBack:
                case RolledBack:
                    // assume that another fiber had a reason to roll back the transaction
                    return False;

                default:
                    throw new IllegalState($"Unexpected status for transaction {idString}: {status}");
                }

            if (distributors.empty)
                {
                status = Distributed;
                return True;
                }

            // distribution is a potentially-cascading process, so start by assuming that everything
            // already modified in the transaction needs to be distributed, and then having done so,
            // assume anything changed by that round of distribution will then need to be
            // distributed as well; repeat until done
            Client<Schema> client = ensureClient(Distributing);
            Set<Int> mayNeedDistribution = enlisted;
            while (!mayNeedDistribution.empty)
                {
                // first, seal everything that may need distribution (so we don't accidentally
                // overwrite any DBObjects that were already modified, and so we don't cascade
                // forever)
                sealById.processAll(mayNeedDistribution, entry ->
                    {
                    assert entry.exists;
                    entry.value ?:= storeFor(entry.key).sealPrepare(writeId);
                    return True;
                    });

                // now distribute the changes
                for (Int storeId : mayNeedDistribution)
                    {
                    if (distributors.contains(storeId) && !client.distributeDBObject(storeId))
                        {
                        return False;
                        }
                    }

                // now collect the newly enlisted (and possibly modified) set of stores
                mayNeedDistribution = takeNewlyEnlisted();

                // sweep any newly enlisted stores (get rid of the ones with no changes)
                for (Int storeId : mayNeedDistribution)
                    {
                    ObjectStore store  = storeFor(storeId);
                    if (store.mergePrepare(writeId, prepareId) == CommittedNoChanges)
                        {
                        sealById.remove(storeId);
                        mayNeedDistribution.remove(storeId);
                        }
                    }
                }

            status = Distributed;
            return True;
            }

        /**
         * Seal the transaction, which generates the pieces of data that will go into the
         * transaction log, and prevents further changes to the transaction.
         *
         * @return True iff the Transaction was successfully sealed
         */
        protected Boolean seal()
            {
            switch (status)
                {
                case Prepared:
                case ReqsChecked:
                case Validated:
                case Rectified:
                case Distributed:
                    checkEnabled();

                    // this has to be the transaction that is currently preparing
                    assert currentlyPreparing == writeId;

                    // if there were nothing enlisted, this would have already "committed"
                    assert !enlisted.empty;

                    status = Sealing;
                    break;

                case Committed:
                    // assume that the transaction already short-circuited to a committed state
                    return True;

                case RollingBack:
                case RolledBack:
                    // assume that another fiber had a reason to roll back the transaction
                    return False;

                default:
                    throw new IllegalState($"Unexpected status for transaction {idString}: {status}");
                }

            // now past all of the prepare stages that need an internal client
            releaseClient();

            for (val entry : sealById.entries)
                {
                Int     storeId = entry.key;
                String? seal    = entry.value;
                if (seal == Null)
                    {
                    entry.value = storeFor(storeId).sealPrepare(writeId);
                    }
                }

            Int prepareId = this.prepareId;
            assert prepareId > 0;
            lastPrepared = prepareId;
            commitId     = prepareId;
            status       = Sealed;
            return True;
            }

        /**
         * Attempt to commit a fully prepared transaction.
         *
         * @return True iff the Transaction was successfully committed
         */
        protected Boolean commit()
            {
            switch (status)
                {
                case Sealed:
                    // this status is the necessary and exact pre-condition for commit
                    break;

                case Committing:
                    @Future Boolean result;
                    addTermination(() -> {result=status==Committed;});
                    return result;

                case Committed:
                    return True;

                case RollingBack:
                case RolledBack:
                    return False;

                default:
                    assert as $"Unexpected status: {status}";
                }

            switch (commitId <=> lastCommitted + 1)
                {
                case Lesser:
                    log($"Attempting to commit transaction {commitId}, but transaction {lastCommitted} was already committed");
                    panic();
                    assert;

                case Equal:
                    // this is the next transaction in line
                    status = Committing;
                    break;

                case Greater:
                    log($"Attempting to commit transaction {commitId}, but the last transaction committed was {lastCommitted}");
                    if (TxRecord prevTx := byWriteId.values.any(tx -> tx.prepareId+1 == commitId))
                        {
                        @Future Boolean result;
                        prevTx.addTermination(() -> {result=this.commit();});
                        return result;
                        }
                    else
                        {
                        log($"Unable to locate pending commit transaction {commitId-1}");
                        panic();
                        assert;
                        }
                }

            if (enlisted.empty)
                {
                // we should not have gotten this far, if nothing is enlisted; the transaction
                // should have already been "pretend-committed" if it had nothing enlisted; now
                // we have no choice but to go through with it, and file an "empty transaction",
                // because otherwise there will be a gap in the numbering
                log($"Error: An empty transaction transaction {idString} was sealed");
                }

            // bundle the results of "sealPrepare()" into a transaction log entry; this is the
            // "official commit" of the transaction
            StringBuffer buf = new StringBuffer();
            addSeal(buf);
            buf.append("\n]");
            appendLog(buf.toString());
            lastCommitted = commitId;

            if (logFile.size > maxLogSize)
                {
                rotateLog();
                }

            // notify the client that the transaction committed
            complete(Committed);

            // now get the ObjectStores to asynchronously write all of their transactional changes
            // to disk
            FutureVar<Tuple<>>? commitAll = Null;
            for (Int storeId : enlisted)
                {
                Tuple<> commitOne = storeFor(storeId).commit^(writeId);
                commitAll = commitAll?.and(&commitOne, (t, _) -> t) : &commitOne; // TODO GG wide ass -> Tuple:()
                }
            assert commitAll != Null;
            commitAll.whenComplete((t, e) ->
                {
                if (e != Null)
                    {
                    log($"Heuristic Commit Exception: During commit of {idString}: {e}");
                    panic();
                    }

                // the writes completed, so advance the safepoint by unregistering the in-flight
                // transaction
                release();
                });

            return True;
            }

        /**
         * Add the transaction record to the passed buffer.
         *
         * @param buf  the buffer to append to
         *
         * @return the passed buffer
         */
        protected StringBuffer addSeal(StringBuffer buf)
            {
            buf.append(",\n{\"_v\":")
               .append(prepareId)
               .append(", \"_tx\":")
               .append(txInfo.id);

            if (String name ?= txInfo.name)
                {
                buf.append(", \"_name\":");
                printString(name, buf);
                }

            buf.append(", \"_ts\":\"")
               .append(clock.now.toString(True))
               .add('\"');

            if (txInfo.retryCount > 0)
                {
                buf.append(", \"_rc\":")
                   .append(txInfo.retryCount);
                }

            Loop: for ((Int storeId, String? json) : sealById)
                {
                assert storeId > 0 && json != Null;

                buf.append(", \"")
                   .append(infoFor(storeId).path.toString().substring(1))   // skip the leading '/'
                   .append("\":")
                   .append(json);
                }

            return buf.append('}');
            }

        /**
         * Attempt to roll back a transaction.
         *
         * @return True iff the Transaction was rolled back
         */
        protected Boolean rollback(CommitResult? reason = Null)
            {
            switch (status)
                {
                case InFlight:
                case Enqueued:
                case Preparing:
                case Prepared:
                case ReqsChecking:
                case ReqsChecked:
                case Validating:
                case Validated:
                case Rectifying:
                case Rectified:
                case Distributing:
                case Distributed:
                case Sealing:
                case Sealed:
                    break;

                case RollingBack:
                case Committing:
                    @Future Boolean result;
                    addTermination(() -> {result=status!=Committed;});
                    return result;

                case Committed:
                    return False;

                case RolledBack:
                    return True;

                default:assert;
                }

            status = RollingBack;
            try
                {
                for (Int storeId : enlisted)
                    {
                    Tuple<> result = storeFor(storeId).rollback^(writeId);
                    &result.handle(e ->
                        {
                        log($"Exception occurred while rolling back transaction {idString} in store {storeId}: {e}");
                        return Tuple:();
                        });
                    }

                return True;
                }
            catch (Exception e)
                {
                log($"Exception occurred while rolling back transaction {idString}: {e}");
                return False;
                }
            finally
                {
                complete(reason ?: RollbackOnly);
                }
            }

        /**
         * Add a callback that will be invoked when the transaction terminates.
         *
         * @param callback  the function to call
         */
        void addTermination(function void() callback)
            {
            function void()? callback2 = terminated;
            terminated = callback2 == Null
                    ? callback
                    : () ->
                        {
                        try
                            {
                            callback();
                            }
                        catch (Exception e)
                            {
                            log($"Exception in termination callback: {e}");
                            }

                        try
                            {
                            callback2();
                            }
                        catch (Exception e)
                            {
                            log($"Exception in termination callback: {e}");
                            }
                        };
            }

        /**
         * Finish the transaction.
         *
         * @param status  the terminal status of the transaction
         */
        protected void complete(CommitResult result)
            {
            // check if the TxRecord already completed
            if (status == Committed || status ==  RolledBack)
                {
                assert pending == Null && terminated == Null;
                assert (result == Committed) == (status == Committed);
                return;
                }

            status = result == Committed ? Committed : RolledBack;

            if (FutureVar<CommitResult> pending ?= this.pending, !pending.assigned)
                {
                pending.complete(result);
                this.pending = Null;
                }

            byClientId.remove(clientId);

            // if the transaction is still being committed asynchronously by any enlisted
            // ObjectStore instances, then do not unregister the transaction (until they have
            // finished writing to disk)
            if (result != Committed || enlisted.empty)
                {
                release();
                }

            releaseClient();

            if (function void() notify ?= terminated)
                {
                terminated = Null;
                notify();
                }

            // clearing the readId will unregister it from the count byReadId
            readId = NO_TX;
            }

        /**
         * Unregister the transaction. This occurs only after the enlisted ObjectStore instances
         * confirm that the commit has completed writing to disk.
         */
        protected void release()
            {
            assert status == Committed || status ==  RolledBack;

            byWriteId.remove(writeId);
            }

        @Override
        String toString()
            {
            StringBuffer buf = new StringBuffer();
            buf.append("TxRecord(writeId=")
               .append(writeId)
               .append(", clientId=")
               .append(clientId)
               .append(", Status=")
               .append(status);

            buf.append(", txInfo=")
               .append(txInfo);

            if (!sealById.empty)
                {
                buf.append(", enlisted={");
                Loop: for ((Int storeId, String? seal) : sealById)
                    {
                    if (!Loop.first)
                        {
                        buf.append(", ");
                        }

                    buf.append(storeId);
                    if (seal != Null)
                        {
                        buf.append('*');
                        }
                    }
                }

            if (!newlyEnlisted.empty)
                {
                buf.append(", newlyEnlisted=")
                   .append(newlyEnlisted);
                }

            if (prepareClient != Null)
                {
                buf.append(", hasClient");
                }

            if (terminated != Null)
                {
                buf.append(", hasTermination");
                }

            return buf.append(')').toString();
            }
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
     *         that the specified "write" transaction id is based on
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     * @throws IllegalState if the transaction state does not allow an ObjectStore to be enlisted,
     *         for example during rollback processing, or after a commit/rollback completes
     */
    Int enlist(Int storeId, Int txId)
        {
        assert isWriteTx(txId);
        checkEnabled();

        TxRecord tx = txFor(txId);

        Int readId = tx.readId;
        if (readId == NO_TX)
            {
            // an enlist into a nascent transaction cannot occur while disabling the transaction
            // manager, or once it has disabled
            checkEnabled();

            // this is the first ObjectStore enlisting for this transaction, so create a mutable
            // set to hold all the enlisting ObjectStores, as they each enlist
            assert tx.enlisted.empty;

            // use the last successfully prepared transaction id as the basis for reading within
            // this transaction; this reduces the potential for roll-back, since prepared
            // transactions always commit unless (i) the plug gets pulled, (ii) a fatal error
            // occurs, or (iii) the database is shut down abruptly
            readId = lastPrepared;
            assert readId != NO_TX;
            tx.readId = readId;
            }

        switch (tx.status)
            {
            case InFlight:
            case Validating:
            case Rectifying:
            case Distributing:
                break;

            default:
                assert as $"Attempt to enlist into a transaction whose status is {tx.status} is forbidden";
            }

        tx.enlist(storeId);

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
        assert TxRecord tx := byWriteId.get(txId);
        return tx.worker;
        }


    // ----- file management  ----------------------------------------------------------------------

    /**
     * Read the TxManager status file.
     *
     * @return the mutable array of LogFileInfo records from the status file, from oldest to newest
     */
    protected conditional LogFileInfo[] readStatus()
        {
        if (!statusFile.exists)
            {
            return False;
            }

        try
            {
            String            json   = statusFile.contents.unpackUtf8();
            ObjectInputStream stream = new ObjectInputStream(internalJsonSchema, json.toReader());
            LogFileInfo[]     infos  = logFileInfoArrayMapping.read(stream.ensureElementInput());
            return True, infos.toArray(Mutable, True);
            }
        catch (Exception e)
            {
            log($"Exception reading TxManager status file: {e}");
            return False;
            }
        }

    /**
     * Read the TxManager status file.
     *
     * @return the array of LogFileInfo records from the status file, from oldest to newest
     */
    protected void writeStatus()
        {
        // render all of the LogFileInfo records as JSON, but put each on its own line
        StringBuffer buf = new StringBuffer();
        using (ObjectOutputStream stream = new ObjectOutputStream(internalJsonSchema, buf))
            {
            Loop: for (LogFileInfo info : logInfos)
                {
                using (ElementOutput out = stream.createElementOutput())
                    {
                    buf.append(",\n");
                    logFileInfoMapping.write(out, info);
                    }
                }
            }

        // wrap it as a JSON array and write it out
        buf[0] = '[';
        buf.append("\n]");
        statusFile.contents = buf.toString().utf8();
        }

    /**
     * Create the transaction log.
     *
     * @return True if the transaction log did not exist, and now it does
     */
    protected Boolean createLog()
        {
        // neither the log nor the status file should exist
        if (logFile.exists || statusFile.exists)
            {
            return False;
            }

        assert lastCommitted == 0;
        assert logInfos.empty;

        // create the log with an entry in it denoting the time the log was created
        logFile.parent?.ensure();
        initLogFile();

        logInfos.add(new LogFileInfo(logFile.name, [1..1), 0, logFile.size, logFile.modified));
        writeStatus();

        return True;
        }

    /**
     * Initialize the "current" transaction log file.
     */
    protected void initLogFile()
        {
        Int safepoint = safepoint;
        logFile.contents = $|[
                            |\{"_op":"created", "_ts":"{clock.now.toString(True)}",\
                            | "_prev_v":{lastCommitted}, "safepoint":{safepoint}}
                            |]
                            .utf8();
        logUpdated(safepoint);
        }

    /**
     * Open the transaction log.
     *
     * @return True if the transaction log did exist, and was successfully opened
     */
    protected Boolean openLog()
        {
        // need to have both the log and the status file
        // load the status file to get a last-known snapshot of the log files
        if (logFile.exists, statusFile.exists, LogFileInfo[] logInfos := readStatus(),
                !logInfos.empty)
            {
            // if the current log file is as-expected, then assume that we're good to go
            LogFileInfo current = logInfos[logInfos.size-1];
            if (current.size == logFile.size && current.timestamp == logFile.modified)
                {
                Int lastCommitted = current.txIds.effectiveUpperBound;
                Int safepoint     = current.safepoint;
                assert safepoint <= lastCommitted;
                if (safepoint < lastCommitted)
                    {
                    return False;
                    }

                // remember the timestamp on the log, as if we just updated it
                logUpdated();

                // append the "we're open" message to the log
                addLogEntry($|\{"_op":"opened", "_ts":"{clock.now.toString(True)}",\
                             | "safepoint":{safepoint}}
                           , safepoint);

                this.logInfos          = logInfos;
                this.lastPrepared      = lastCommitted;
                this.lastCommitted     = lastCommitted;
                this.previousSafepoint = safepoint;

                return True;
                }
            }

        return False;
        }

    /**
     * Make a "best attempt" to automatically recover the transaction log and the database.
     *
     * @return True if the transaction log did exist, and was successfully recovered, and the
     *         impacted ObjectStore instances were also recovered
     */
    protected Boolean recoverLog()
        {
        // start by trying to read the status file
        LogFileInfo[] logInfos;
        Int           lastTx    = -1;
        Int           safepoint = -1;
        if (logInfos := readStatus())
            {
            // validate the LogFileInfo entries; assume that older files may have been deleted, and
            // that the latest size/timestamp for the current logFile may not have been recorded
            Int firstIndex = 0;
            Loop: for (LogFileInfo oldInfo : logInfos)
                {
                File infoFile = sysDir.fileFor(oldInfo.name);
                if (infoFile.exists)
                    {
                    LogFileInfo newInfo;
                    try
                        {
                        newInfo = loadLog(infoFile);
                        }
                    catch (Exception e)
                        {
                        log($|TxManager was unable to load the log file {logFile} due to an\
                             | exception; abandoning automatic recovery; exception: {e}
                           );
                        return False;
                        }

                    if (newInfo.txIds.size > 0)
                        {
                        Int firstSegmentTx = newInfo.txIds.effectiveFirst;
                        Int lastSegmentTx  = newInfo.txIds.effectiveUpperBound;
                        if (lastTx < 0 || firstSegmentTx == lastTx+1
                                && (newInfo.txIds.size == 0 || lastSegmentTx > lastTx))
                            {
                            if (newInfo.txIds.size > 0)
                                {
                                lastTx = lastSegmentTx;
                                }
                            }
                        else
                            {
                            log($|TxManager log files {logInfos[Loop.count-1].name} and {newInfo.name} do\
                                 | not hold contiguous transaction ranges; abandoning automatic recovery
                               );
                            return False;
                            }
                        }

                    safepoint = newInfo.safepoint;
                    logInfos[Loop.count] = newInfo;
                    }
                else
                    {
                    if (Loop.count == firstIndex)
                        {
                        if (infoFile.name == logFile.name)
                            {
                            log($|TxManager automatic recovery was unable to load the current\
                                 | segment of the transaction log, because it does not exist;\
                                 | an empty file will be created automatically
                               );
                            }
                        else
                            {
                            log($|TxManager was unable to load a historical segment of the\
                                 | transaction log from file {infoFile}, because the file does not\
                                 | exist; the missing information is assumed to have been archived\
                                 | or purged; recovery will continue
                               );
                            }

                        ++firstIndex;
                        }
                    else if (oldInfo.txIds.size > 0)
                        {
                        log($|TxManager was unable to load the log file {infoFile}, because the file\
                             | does not exist; abandoning automatic recovery
                           );
                        return False;
                        }
                    else
                        {
                        log($|TxManager was unable to load the log file {infoFile}, because the\
                             | file does not exist; however, it contained no transactional records,\
                             | so recovery will continue
                           );
                        }
                    }
                }

             if (firstIndex > 0)
                {
                logInfos = firstIndex == logInfos.size
                        ? logInfos.clear()
                        : logInfos.slice([firstIndex..logInfos.size)).reify(Mutable);
                }
            }
        else
            {
            log("TxManager status file was not recoverable");

            // scan the directory for log files
            File[] logFiles = findLogs();
            if (logFiles.empty)
                {
                log("TxManager failed to locate any log files to recover; abandoning automatic recovery");
                return False;
                }

            // parse and order the log files
            logInfos = loadLogs(logFiles);

            // verify that the transactions are ordered and contiguous
            Loop: for (LogFileInfo info : logInfos)
                {
                if (lastTx >= 0)
                    {
                    if (lastTx == info.txIds.effectiveFirst-1)
                        {
                        if (info.txIds.size > 0)
                            {
                            lastTx = info.txIds.effectiveUpperBound;
                            }
                        }
                    else
                        {
                        log($|TxManager log files {logInfos[Loop.count-1].name} and {info.name} do\
                             | not hold contiguous transaction ranges; abandoning automatic recovery
                           );
                        return False;
                        }
                    }
                else
                    {
                    lastTx = info.txIds.effectiveUpperBound;
                    assert lastTx >= 0;
                    }

                safepoint = info.safepoint;
                }
            }

        // create the "current" log file, if one does not exist
        if (logFile.exists)
            {
            assert lastTx >= 0 && safepoint >= 0;

            // append a log recovery message to the log
            addLogEntry($|\{"_op":"recovered", "_ts":"{clock.now.toString(True)}",\
                         | "safepoint":{safepoint}}
                       , safepoint);
            logInfos[logInfos.size-1] = logInfos[logInfos.size-1].with(size=logFile.size,
                                                                       timestamp=logFile.modified);
            }
        else
            {
            log("TxManager current segment of transaction log is missing, so one will be created");
            String timestamp = clock.now.toString(True);
            logFile.contents = $|[
                                |\{"_op":"created", "_ts":"{timestamp}", "_prev_v":{lastTx},\
                                | "safepoint":{safepoint}},
                                |\{"_op":"recovered", "_ts":"{timestamp}", "safepoint":{safepoint}}
                                |]
                                .utf8();

            safepoint = safepoint.maxOf(0);
            LogFileInfo info = new LogFileInfo(logFile.name, [lastTx+1..lastTx+1), safepoint, logFile.size, logFile.modified);
            if (logInfos[logInfos.size-1].name == logFile.name)
                {
                logInfos[logInfos.size-1] = info;
                }
            else
                {
                logInfos += info;
                }
            }

        // rebuild the status file
        this.logInfos          = logInfos;
        this.previousSafepoint = safepoint;
        writeStatus();

        // rebuild the ObjectStore instances
        if (safepoint < lastTx)
            {
            if (recoverStores(safepoint, lastTx))
                {
                safepoint = lastCommitted;

                // append a database recovery message to the log (the safepoint is now caught up)
                addLogEntry($|\{"_op":"recovered", "_ts":"{clock.now.toString(True)}",\
                             | "safepoint":{safepoint}}
                           , safepoint);

                Int last = logInfos.size-1;
                logInfos[last] = logInfos[last].with(safepoint=safepoint);
                writeStatus();
                }
            else
                {
                log($|TxManager failed to replay log from known safepoint {safepoint} to latest\
                     | transaction {lastTx}
                   );
                return False;
                }
            }

        log("TxManager automatic recovery completed");
        return openLog();
        }

    /**
     * Load the transaction log that ranges from the `safepoint` to the `lastTx`, and apply those
     * transactions to all of the ObjectStore instances that were enlisted in those transactions,
     * to ensure that the storage for those ObjectStore instances is up-to-date with the transaction
     * log.
     *
     * @return True if the transaction log did exist, and was successfully recovered, and the
     *         impacted ObjectStore instances were also recovered
     */
    protected Boolean recoverStores(Int safepoint, Int lastTx)
        {
        SkiplistMap<Int, SkiplistMap<Int, Token[]>> replayByDboId = new SkiplistMap();
        SkiplistMap<String, Int>                    dboIdByPath   = new SkiplistMap();

        // first, determine all of the impacted ObjectStores, and load all of the transactions in
        // the recovery range that need to be replayed to those ObjectStores
        Int firstTx = safepoint+1;
        if (Int logInfoIndex := findLogInfoIndex(firstTx))
            {
            log($"Loading transactions {firstTx}..{lastTx} for replay");
            for ( ; logInfoIndex < logInfos.size; ++logInfoIndex)
                {
                log($"Loading transaction log file {logInfos[logInfoIndex].name}");
                assert File logFile := sysDir.findFile(logInfos[logInfoIndex].name);
                String json = logFile.contents.unpackUtf8();
                using (Parser logParser = new Parser(json.toReader()))
                    {
                    using (val arrayParser = logParser.expectArray())
                        {
                        NextEntry: while (!arrayParser.eof)
                            {
                            using (val objectParser = arrayParser.expectObject())
                                {
                                if (objectParser.matchKey("_v"))
                                    {
                                    Int txId = objectParser.expectInt();
                                    if (txId >= firstTx && txId <= lastTx)
                                        {
                                        // every non-"_" key is a path to a DBObject
                                        while (Token token := objectParser.matchKey())
                                            {
                                            String name = token.value.toString();
                                            if (name.startsWith('_'))
                                                {
                                                objectParser.skip();
                                                }
                                            else
                                                {
                                                Int dboId = dboIdByPath.computeIfAbsent(name,
                                                        () -> catalog.infoFor(name).id);
                                                Token[] seal = new Token[];
                                                objectParser.skip(seal);
                                                assert replayByDboId.computeIfAbsent(dboId,
                                                        ()->new SkiplistMap())
                                                        .putIfAbsent(txId, seal);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        else
            {
            log($"Could not locate transaction {firstTx} in the transaction log");
            return False;
            }

        // scan the effected ObjectStore images
        ObjectStore[] repair = new ObjectStore[];
        for (Int dboId : replayByDboId.keys)
            {
            DBObjectInfo info = catalog.infoFor(dboId);
            if (info.lifeCycle != Removed)
                {
                // get the storage
                ObjectStore store = storeFor(info.id);

                // validate the storage
                try
                    {
                    if (store.deepScan())
                        {
                        continue;
                        }

                    log($"During recovery, corruption was detected in \"{info.path}\"");
                    }
                catch (Exception e)
                    {
                    log($"During recovery, an error occurred in the deepScan() of \"{info.path}\": {e}");
                    }

                repair += store;
                }
            }

        // try to repair any damaged ObjectStore images
        Boolean error = False;
        for (ObjectStore store : repair)
            {
            try
                {
                // repair the storage
                if (store.deepScan(fix=True))
                    {
                    continue;
                    }

                log($"During recovery, unable to fix \"{store.info.path}\"");
                }
            catch (Exception e)
                {
                log($"During recovery, unable to fix \"{store.info.path}\" due to an error: {e}");
                }

            error = True;
            }
        if (error)
            {
            return False;
            }

        // finally, for each ObjectStores impacted by any of those transactions, instruct it to
        // apply all transactions from the recovery range that it was enlisted in
        for ((Int dboId, SkiplistMap<Int, Token[]> seals) : replayByDboId)
            {
            DBObjectInfo info = catalog.infoFor(dboId);
            if (info.lifeCycle != Removed)
                {
                // get the storage
                ObjectStore store = storeFor(info.id);
                try
                    {
                    // recover the storage by applying all the potentially missing tx data
                    if (store.recover(seals))
                        {
                        continue;
                        }

                    log($"During recovery, unable to recover \"{store.info.path}\"");
                    }
                catch (Exception e)
                    {
                    log($"During recovery, unable to recover \"{store.info.path}\" due to an error: {e}");
                    }

                error = True;
                }
            }

        return !error;
        }

    /**
     * Determine if the passed file is a log file.
     *
     * @param file  a possible log file
     *
     * @return the DateTime that the LogFile was rotated, or Null if it is the current log file
     */
    static conditional DateTime? isLogFile(File file)
        {
        String name = file.name;
        if (name == "txlog.json")
            {
            return True, Null;
            }

        if (name.startsWith("txlog_") && name.endsWith(".json"))
            {
            String timestamp = name[6..name.size-5);
            try
                {
                return True, new DateTime(timestamp);
                }
            catch (Exception e) {}
            }

        return False;
        }

    /**
     * Compare two log files for order.
     *
     * @param file1  the first log file
     * @param file2  the second log file
     *
     * @return the order to sort the two files into
     */
    static Ordered orderLogFiles(File file1, File file2)
        {
        assert DateTime? dt1 := isLogFile(file1);
        assert DateTime? dt2 := isLogFile(file2);

        // sort the null datetime to the end, because it represents the "current" log file
        return dt1? <=> dt2? : switch (dt1, dt2)
            {
            case (Null, Null): Equal;
            case (Null, _): Greater;
            case (_, Null): Lesser;
            default: assert;
            };
        }

    /**
     * Find all of the log files.
     *
     * @return a mutable array of log files, sorted from oldest to current
     */
    protected File[] findLogs()
        {
        return sysDir.files()
                .filter(f -> isLogFile(f))
                .sorted(orderLogFiles)
                .toArray(Mutable);
        }

    /**
     * Obtain corresponding LogFileInfo objects for each of the specified log files.
     *
     * @param logFiles an array of log files
     *
     * @return a mutable array of [LogFileInfo]
     */
    protected LogFileInfo[] loadLogs(File[] logFiles)
        {
        return logFiles.map(f -> loadLog(f), new LogFileInfo[]).as(LogFileInfo[]).toArray(Mutable, True);
        }

    /**
     * Obtain the LogFileInfo for the specified log file.
     *
     * @param logFile a log file
     *
     * @return the [LogFileInfo] for the file
     */
    protected LogFileInfo loadLog(File logFile)
        {
        String json = logFile.contents.unpackUtf8();

        Int first = -1;
        Int last  = -1;
        Int safe  = -1;
        using (Parser logParser = new Parser(json.toReader()))
            {
            using (val arrayParser = logParser.expectArray())
                {
                NextEntry: while (!arrayParser.eof)
                    {
                    using (val objectParser = arrayParser.expectObject())
                        {
                        Int txId = -1;
                        if (objectParser.matchKey("_v"))
                            {
                            txId = objectParser.expectInt();
                            }
                        else if (objectParser.matchKey("_op"))
                            {
                            switch (objectParser.expectString())
                                {
                                case "created":
                                    if (objectParser.findKey("_prev_v"))
                                        {
                                        txId = objectParser.expectInt() + 1;
                                        }
                                    continue;
                                default:
                                    if (objectParser.findKey("safepoint"))
                                        {
                                        safe = objectParser.expectInt();
                                        }
                                    break;
                                }
                            }

                        if (txId > last)
                            {
                            if (first < 0)
                                {
                                first = txId;
                                }
                            last = txId;
                            }
                        }
                    }
                }
            }

        assert first >= 0 && safe >= 0 as $"Log file \"{logFile.name}\" is missing required header information";
        return new LogFileInfo(logFile.name, first..last, safe, logFile.size, logFile.modified);
        }

    /**
     * Find the log file that contains the specified transaction.
     *
     * @param txId  the internal transaction id (version number)
     *
     * @return True iff the `txId` exists in the log
     * @return (conditional) the log file index that contains the `txId`
     */
    protected conditional Int findLogInfoIndex(Int txId)
        {
        LogFileInfo[] logInfos = logInfos;
        if (logInfos.empty || txId < logInfos[0].txIds.effectiveLowerBound
                || txId > logInfos[logInfos.size-1].txIds.effectiveUpperBound)
            {
            return False;
            }

        // search backward through the log
        Int logInfoIndex = logInfos.size-1;
        while (logInfoIndex >= 0)
            {
            Range<Int> txIds = logInfos[logInfoIndex].txIds;
            if (txIds.contains(txId))
                {
                return True, logInfoIndex;
                }
            --logInfoIndex;
            }

        return False;
            }

    /**
     * Append a log entry to the log file.
     *
     * @param entry      a JSON object, rendered as a string, to append to the log
     * @param safepoint  non-null iff the safepoint is being updated as part of the log update
     */
    protected void addLogEntry(String entry, Int? safepoint=Null)
        {
        appendLog($",\n{entry}\n]", safepoint);
        }

    /**
     * Append a buffer to the log file.
     *
     * @param buf        the buffer containing the text to append to the log
     * @param safepoint  non-null iff the safepoint is being updated as part of the log update
     */
    protected void appendLog(String s, Int? safepoint=Null)
        {
        validateLog();

        File file   = logFile;
        Int  length = file.size;
        assert length >= 20;        // log file is never empty!

        file.truncate(length-2)
            .append(s.utf8());

        logUpdated(safepoint);
        }

    /**
     * Make sure that the log is safe to append to.
     */
    protected void validateLog()
        {
        if (expectedLogTimestamp != logFile.modified)
            {
            log($"Log file {logFile.name} appears to have been modified externally; the expected timestamp was {expectedLogTimestamp} (TODO)");
            // TODO this is where the log file is "rebuilt automatically" like in the log recovery stage
            }
        }

    /**
     * Record the timestamp on the log after it was updated.
     *
     * @param safepoint  non-null iff the safepoint was updated as part of the log update
     */
    protected void logUpdated(Int? safepoint=Null)
        {
        expectedLogTimestamp = logFile.modified;
        previousSafepoint    = safepoint?;
        }

    /**
     * Move the current log to an archived state, and create a new, empty log.
     */
    protected void rotateLog()
        {
        assert !logInfos.empty;

        String timestamp = clock.now.toString(True);
        Int    safepoint = this.safepoint;
        addLogEntry($|\{"_op":"archived", "_ts":"{timestamp}", "safepoint":{safepoint}}
                   );

        String rotatedName = $"txlog_{timestamp}.json";
        assert File rotatedFile := logFile.renameTo(rotatedName);

        LogFileInfo previousInfo = logInfos[logInfos.size-1];
        LogFileInfo rotatedInfo  = new LogFileInfo(rotatedName, previousInfo.txIds.first..lastCommitted,
                safepoint, rotatedFile.size, rotatedFile.modified);

        initLogFile();
        LogFileInfo currentInfo = new LogFileInfo(logFile.name, [lastCommitted+1..lastCommitted+1),
                safepoint, logFile.size, logFile.modified);

        logInfos[logInfos.size-1] = rotatedInfo;
        logInfos += currentInfo;
        writeStatus();
        }

    /**
     * Mark the transaction log as being closed.
     */
    protected void closeLog()
        {
        // the database should have quiesced by now
        assert lastCommitted == safepoint;

        addLogEntry($|\{"_op":"closed", "_ts":"{clock.now.toString(True)}", "safepoint":{safepoint}}
                   );

        // update the "current log file" status record
        LogFileInfo oldInfo = logInfos[logInfos.size-1];
        assert oldInfo.name == logFile.name;

        Range<Int> txIds = oldInfo.txIds;
        if (lastCommitted > txIds.effectiveUpperBound)
            {
            txIds = txIds.effectiveLowerBound .. lastCommitted;
            }

        LogFileInfo newInfo = new LogFileInfo(logFile.name, txIds, safepoint, logFile.size, logFile.modified);
        logInfos[logInfos.size-1] = newInfo;
        writeStatus();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Log a message to the system log.
     *
     * @param msg  the message to log
     */
    protected void log(String msg)
        {
        catalog.log^(msg);
        }

    /**
     * Calmly respond to an irrecoverable disaster.
     */
    protected void panic()
        {
        // TODO halt the db before anything gets corrupted
        log("TxManager PANIC!!!");
        }

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    protected DBObjectInfo infoFor(Int id)
        {
        if (id < 0)
            {
            // these are non-transactional, so there is no expectation that this will ever occur
            return Catalog.BuiltIn.byId(id).info;
            }

        Int size = infos.size;
        if (id < size)
            {
            return infos[id]?;
            }

        DBObjectInfo info = catalog.infoFor(id);

        // save off the ObjectStore (lazy cache)
        infos[id] = info;

        return info;
        }

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the ObjectStore for the specified id
     */
    protected ObjectStore storeFor(Int id)
        {
        ObjectStore?[] stores = appStores;
        Int            index  = id;
        if (id < 0)
            {
            stores = sysStores;
            index  = Catalog.BuiltIn.byId(id).ordinal;
            }

        Int size = stores.size;
        if (index < size)
            {
            return stores[index]?;
            }

        ObjectStore store = catalog.storeFor(id);

        // save off the ObjectStore (lazy cache)
        stores[index] = store;

        return store;
        }

    /**
     * Obtain a Client object from an internal pool of Client objects. These Client objects are used
     * to represent specific stages of transaction processing. When the use of the Client is
     * finished, it should be returned to the pool by passing it to [recycleClient].
     *
     * @return an "internal" Client object
     */
    Client<Schema> allocateClient()
        {
        return clientCache.takeOrCompute(() -> catalog.createClient(system=True));
        }

    /**
     * Return the passed Client object to the internal pool of Client objects.
     *
     * @param client  an "internal" Client object previously obtained from [allocateClient]
     */
    void recycleClient(Client<Schema> client)
        {
        return clientCache.reversed.add(client);
        }
    }
