import Catalog.Status;
import model.DBObjectInfo;
import oodb.DBObject.DBCategory as Category;
import json.Doc;


/**
 * This is an abstract base class for storage services, each of which manages information-on-disk on
 * behalf of one DBObject, with that information encoded in a JSON format. The abstraction is also
 * designed to allow support for caching optimizations to be layered on top of a persistent storage
 * implementation -- probably more to avoid expensive serialization and deserialization, than to
 * avoid raw I/O operations (which are expected to be low-cost in NVMe-flash and SAN environments).
 *
 * ObjectStore represents the mapping between binary storage on disk using files and directories,
 * and the fundamental set of operations that a particular DBObject interface (such as [DBMap] or
 * [DBList]) requires and uses to provide its high-level functionality.
 *
 * Each DBObject in the database has a corresponding ObjectStore instance that manages its
 * persistent data on disk.
 *
 * When an ObjectStore is constructed, it is in an initial, inert mode; it does not interact at all
 * with the disk storage until it is explicitly instructed to do so. Normally, the ObjectStore
 * assumes that the storage is either absent (if the database is newly created) or intact (from some
 * previous shut-down), and because it is not assuming corrupted information on disk, it takes a
 * fast-path to initialize its information about the disk contents; this is called [quickScan]. If
 * recovery of the storage is indicated or instructed, then the ObjectStore uses a [deepScan], which
 * is assumed to be far more intensive both in terms of IO activity and computational time, and may
 * even be "destructive" in the sense that it is expected to return the files and directories on
 * disk to a working and uncorrupted state, and that may force the ObjectStore implementation to
 * discard information that cannot be safely recovered.
 *
 * TODO background maintenance
 */
service ObjectStore(Catalog catalog, DBObjectInfo info, Appender<String> errs)
        implements Hashable
        implements Closeable
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Catalog that this ObjectStore belongs to. The Catalog reference is provided as part of
     * instantiation, and never changes.
     */
    public/private Catalog catalog;

    /**
     * The transaction manager that this ObjectStore is being managed by. A reference to the
     * TxManager is lazily obtained from the Catalog service and then cached here, to avoid service
     * hopping just to get a reference to the TxManager every time that it is needed.
     */
    protected @Lazy TxManager txManager.calc()
        {
        return catalog.txManager;
        }

    /**
     * The DBObjectInfo that identifies the configuration of this ObjectStore. The information is
     * provided as part of instantiation, and never changes.
     */
    public/private DBObjectInfo info;

    /**
     * The id of the `DBObject` for which this storage exists.
     */
    Int id.get()
        {
        return info.id;
        }

    /**
     * The `DBCategory` of the `DBObject` for which this storage exists.
     */
    Category category.get()
        {
        return info.category;
        }

    /**
     * An error log that was provided to this storage when it was created, for the purpose of
     * logging detailed error information encountered in the course of operation.
     */
    public/protected Appender<String> errs;

    /**
     * The current [Status] of this ObjectStore.
     */
    public/protected Status status = Closed;

    /**
     * True iff the ObjectStore is permitted to write to persistent storage. Implementations of
     * ObjectStore must check this value before making any changes to persistent storage.
     */
    public/protected Boolean writeable = False;

    /**
     * The path that specifies this `DBObject`, and that implies the directory location for the data
     * managed by this `ObjectStore` and represented by the corresponding `DBObject`.
     */
    Path path.get()
        {
        return info.path;
        }

    /**
     * The directory within which the named ObjectStore file or directory exists. That file or
     * directory is used to load data from and store data into by this ObjectStore. Generally, the
     * ObjectStore does not use the `containingDir` directly.
     */
    @Lazy public/private Directory containingDir.calc()
        {
        // TODO this should be a lot easier ... e.g.: return catalog.dir.apply(path);
        Directory dir = catalog.dir;
        for (Path part : path[1..path.size-1))
            {
            dir = dir.dirFor(part.name);
            }
        return dir;
        }

    /**
     * The directory owned by this ObjectStore for purpose of its data storage. The ObjectStore may
     * create and remove this directory, and may create, modify, and remove any files within this
     * directory, except for any `FileNode`s in the directory whose names match any of the names
     * of child `DBObject`s of the `DBObject` that this ObjectStore corresponds to. (In other words,
     * if the corresponding `DBObject` has children named `A`, `B`, and `C`, then any file or
     * directory with any name can be created, modified, and removed by this ObjectStore except for
     * the file nodes named `A`, `B`, or `C`.)
     */
    @Lazy public/private Directory dataDir.calc()
        {
        return containingDir.dirFor(info.name);
        }

    /**
     * Statistics: The estimated number of bytes on disk in use by this storage object.
     */
    public/protected Int bytesUsed = 0;

    /**
     * Statistics: The estimated number of file on disk in use by this storage object.
     */
    public/protected Int filesUsed = 0;

    /**
     * * Empty - Either no storage has been allocated, or the contents are absent, so the storage
     *   is currently optimized for an absence of data.
     * * Small - The amount of data can be easily loaded into, manipulated in, and even held (if
     *   necessary and/or desirable) in memory.
     * * Medium - The amount of data is such that it can be managed in memory when necessary.
     * * Large - The amount of data is large enough that it should paged into memory only in
     *           portions at any given time.
     */
    enum StorageModel {Empty, Small, Medium, Large}

    /**
     * Statistics: The current storage model for this ObjectStore.
     */
    public/protected StorageModel model = Empty;

    /**
     * Statistics: The last time that the storage was accessed. Null indicates no record of access.
     *
     * This property will only have a dependable value when the storage is not closed and/or has
     * completed either a quick scan or a deep scan.
     */
    public/protected DateTime? lastAccessed = Null;

    /**
     * Statistics: The last time that the storage was modified. Null indicates no record of
     * modification.
     *
     * This property will only have a dependable value when the storage is not closed and/or has
     * completed either a quick scan or a deep scan.
     */
    public/protected DateTime? lastModified = Null;

    /**
     * Determine if this ObjectStore for a DBObject is allowed to write to disk. True iff the
     * catalog is not read only and the DBObject has not been deprecated or removed.
     */
    protected Boolean defaultWriteable.get()
        {
        return !catalog.readOnly && info.id > 0 && info.lifeCycle == Current;
        }

    /**
     * An internal, mutable record of Changes for a specific transaction.
     */
    protected class Changes(Int writeId, Int readId)
        {
        /**
         * This txId, the "write" txId.
         */
        Int writeId;

        /**
         * The read txId that this transaction is based from. (Note that the "read" id may itself be
         * a "write" id, which will occur during the prepare phase when a Rectifier or Distributor
         * is being executed, and the thus-far-prepared transaction is visible by the read id.)
         */
        Int readId;

        /**
         * The worker to dump CPU intensive serialization and deserialization work onto.
         */
        @Lazy Client.Worker worker.calc()
            {
            return txManager.workerFor(writeId);
            }

        /**
         * Set to True when the transaction contains possible changes related to this ObjectStore.
         */
        Boolean modified;

        /**
         * Set to True when the transaction has been prepared. Note that the prepare phase can
         * involve several steps, so changes from `Rectifier` and `Distributor` objects can occur
         * after the transaction is marked as having been prepared.
         */
        Boolean prepared;

        /**
         * Set to True when the transaction has been sealed, disallowing further changes.
         */
        Boolean sealed;
        }

    /**
     * In flight transactions involving this ObjectStore, keyed by "write" transaction id. This
     * property is required to be initialized only by transactional instances of ObjectStore.
     */
    @Unassigned protected SkiplistMap<Int, Changes> inFlight;

    /**
     * An ObjectStore can be processing up to one transaction at a time **in the prepare phase**. In
     * other words, "prepare" is the one phase that is not pipeline-able through an ObjectStore, for
     * two reasons:
     *
     * * The resulting prepared state of one transaction serves as the basis for validating the
     *   next, which implies a deliberately sequential process, and since the transactions can
     *   involve multiple ObjectStores preparing concurrently, the second prepare cannot begin until
     *   all ObjectStores successfully prepare the first (unless an expensive, optimistic approach
     *   is used, which could prepare subsequent transactions, and then undo and retry those
     *   transactions as necessary, based on the detection of failure in preceding prepares);
     *
     * * Synchronous triggers (represented by the Validator, Rectifier, and Distributor interfaces)
     *   are an even more significant barrier to pipelining, because they both require a stable
     *   transactional demarcation (snapshot of the database as of a prepare point) against which to
     *   execute, **and** they can modify the database, with those modifications being required to
     *   be visible by the time that the next transaction begins to prepare.
     *
     */
    Changes? inPrepare;


    // ----- life cycle ----------------------------------------------------------------------------

    /**
     * For a closed ObjectStore, examine the contents of the persistent storage and recover from
     * that to a running state if at all possible.
     *
     * @throws IllegalState  if the ObjectStore is not `Closed`
     */
    Boolean recover()
        {
        assert status == Closed;
        status = Recovering;
        if (deepScan(True))
            {
            status    = Running;
            writeable = defaultWriteable;
            return True;
            }

        status    = Closed;
        writeable = False;
        return False;
        }

    /**
     * For a closed ObjectStore, quickly open the contents of the persistent storage in order to
     * achieve a running state.
     *
     * @throws IllegalState  if the ObjectStore is not `Closed`
     */
    Boolean open()
        {
        assert status == Closed;
        if (quickScan())
            {
            status    = Running;
            writeable = defaultWriteable;
            return True;
            }

        status    = Closed;
        writeable = False;
        return False;
        }

    /**
     * Close this `ObjectStore`.
     */
    @Override
    void close(Exception? cause = Null)
        {
        switch (status)
            {
            case Recovering:
                // there is nothing that the generic ObjectStore can do
                break;

            case Running:
                // there is nothing for the generic ObjectStore to do
                break;

            case Closed:
                break;

            case Configuring:
                assert as $"Illegal attempt to close {info.name.quoted()} storage while Configuring";

            default:
                assert as $"Illegal status: {status}";
            }

        status    = Closed;
        writeable = False;
        }

    /**
     * Delete the contents of the ObjectStore's persistent data.
     */
    void delete()
        {
        switch (status)
            {
            case Recovering:
            case Configuring:
            case Closed:
                model        = Empty;
                filesUsed    = 0;
                bytesUsed    = 0;
                lastAccessed = Null;
                lastModified = Null;

                Directory dir = dataDir;
                if (dir.exists)
                    {
                    dir.deleteRecursively();
                    }
                break;

            case Running:
                // there is nothing for the generic ObjectStore to do
                assert as $"Illegal attempt to delete {info.name.quoted()} storage while running";

            default:
                assert as $"Illegal status: {status}";
            }

        }


    // ----- transaction handling ------------------------------------------------------------------

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
     * Verify that the storage is open and can read.
     *
     * @return True if the specified transaction is permitted to read data from the database
     *
     * @throws Exception if the check fails
     */
    Boolean checkRead()
        {
        return status == Recovering || status == Running
            || throw new IllegalState($"Read is not permitted for {info.name.quoted()} storage when status is {status}");
        }

    /**
     * Verify that the storage is open and can write.
     *
     * @param txId  the transaction id
     *
     * @return True if the specified transaction is permitted to make changes
     *
     * @throws Exception if the check fails
     */
    Boolean checkWrite()
        {
        return (writeable
                || throw new IllegalState($"Write is not enabled for the {info.name.quoted()} storage"))
            && (status == Recovering || status == Running
                || throw new IllegalState($"Write is not permitted for {info.name.quoted()} storage when status is {status}"));
        }

    /**
     * Validate the transaction, and obtain the transactional information for the specified id.
     *
     * @param txId     the transaction id
     * @param writing  (optional) pass True if a database modification by the current operation is
     *                 expected
     *
     * @return True if the transaction is a write transaction
     * @return (conditional) the Changes record for the transaction
     */
    conditional Changes checkTx(Int txId, Boolean writing=False)
        {
        if (isWriteTx(txId))
            {
            checkWrite();
TODO GG
//            return True, inFlight.computeIfAbsent(txId,
//                    () -> new Changes(txId, txManager.enlist(this, txId)));
            }
        else if (writing)
            {
            throw new IllegalState($"An attempt to modify data within a read-only transaction ({txId}).");
            }
        else
            {
            assert isReadTx(txId);
            checkRead();
            return False;
            }
        }

    /**
     * Validate that the transaction ID is a write ID, and see if a transaction exists for that
     * write ID on this ObjectStore.
     *
     * @param writeId  the transaction id
     *
     * @return True if the transaction exists on this ObjectStore
     * @return (conditional) the Changes record for the transaction
     */
    conditional Changes peekTx(Int writeId)
        {
        assert isWriteTx(writeId);
        return inFlight.get(writeId);
        }

    /**
     * Possible outcomes from a [prepare] call:
     *
     * * FailedRolledBack indicates that the prepare failed, and this ObjectStore has rolled back
     *   all of its data associated with the specified transaction.
     *
     * * CommittedNoChanges indicates that the prepare succeeded, but that there were no changes,
     *   so an implicit commit has already occurred for this transaction on this ObjectStore.
     *
     * * Prepared indicates that there were changes, and they were successfully prepared; once
     *   the prepare stage has completed, changes are subject to Validator evaluation, then
     *   Rectifier evaluation, then Distributor evaluation (repeated until no further distribution
     *   is triggered), and then finally the accrued changes (including a record of any triggered
     *   AsyncTrigger objects) are committed.
     */
    enum PrepareResult {FailedRolledBack, CommittedNoChanges, Prepared}

    /**
     * Prepare the specified transaction.
     *
     * If the the result of the `prepare()` operation is `FailedRolledBack` or `CommittedNoChanges`,
     * then the `ObjectStore` will have forgotten its `Changes` (the `writeId`) by the time that
     * this method returns, and nothing will be associated with the `prepareId`.
     *
     * If the the result of the `prepare()` operation is `Prepared`, then all of the `Changes` data
     * will now be associated with the `prepareId`, and the `writeId` will be associated with an
     * empty set of changes.
     *
     * @param writeId    the "write" transaction id that was used to collect the transactional
     *                   changes
     * @param prepareId  the "read" transaction id that the prepared data will be moved to in
     *                   preparation for a commit
     *
     * @return a [PrepareResult] indicating the result of the `prepare()` operation
     */
    PrepareResult prepare(Int writeId, Int prepareId)
        {
        TODO
        }

    /**
     * Possible outcomes from a [prepare] call:
     *
     * * `NoMerge` indicates that there were no changes to merge.
     *
     * * `CommittedNoChanges` indicates that there were changes to merge, but the result of merging
     *   those changes undid the previously prepared changes for this one ObjectStore, because the
     *   merged changes perfectly negated the previously prepared changes.
     *
     * * `Merged` indicates that there were changes to merge, and they were successfully merged into
     *   the `prepareId` transaction.
     */
    enum MergeResult {NoMerge, CommittedNoChanges, Merged}

    /**
     * Move changes from the specified `writeId` transaction into its corresponding `readId`
     * transaction (its `prepareId`, since this is only used for transactions that are preparing).
     * Merging the changes into the destination transaction, leaving the source transaction empty.
     *
     * @param writeId    the "write" transaction id that may have collected additional transactional
     *                   changes
     * @param prepareId  the "read" transaction id that the prepared data will be moved to in
     *                   preparation for a commit
     * @param seal       (optional) True indicates that, after the merge is complete, this
     *                   ObjectStore must reject any additional changes to the specified transaction
     *
     * @return a [MergeResult] indicating the result of the `mergePrepare()` operation
     */
    MergeResult mergePrepare(Int writeId, Int prepareId, Boolean seal = False)
        {
        TODO
        }

    /**
     * Commit a group of previously prepared transactions. When this method returns, the
     * transactional changes enumerated in the returned map related to this ObjectStore will have
     * been successfully committed to disk.
     *
     * @param writeIdForPrepareId  a Map, keyed by "prepareId", with the corresponding value being
     *                             the "writeId"
     *
     * @return the ObjectStore's commit records, as JSON documents, keyed by prepare (aka commit) id
     *
     * @throws Exception on any failure
     */
    OrderedMap<Int, Doc> commit(OrderedMap<Int, Int> writeIdForPrepareId)
        {
        TODO
        }

    /**
     * Roll back any transactional data associated with the specified transaction id. When this
     * method returns, the transactional changes related to this ObjectStore will have been
     * discarded, including any prepared changes, and the `writeId` (and the `prepareId`, if one
     * exists) will have been discarded.
     *
     * @param writeId  a "write" transaction id that specifies the transaction (even though the data
     *                 from the transaction may have been moved to a `prepareId` by the time that
     *                 this method is called)
     *
     * @throws Exception on hard failure
     */
    void rollback(Int writeId)
        {
        TODO
        }

    /**
     * Inform the ObjectStore of all of the read-transaction-ids that are still being relied upon
     * by in-flight transactions; any other historical transaction information can be discarded by
     * the ObjectStore, both in-memory and in the persistent storage. For asynchronous purposes, any
     * transaction newer than the most recent transaction in the passed set must be retained.
     *
     * @param inUseTxIds  an ordered set of read transaction ids whose information needs to be
     *                    retained by the `ObjectStore`
     * @param force       (optional) specify True to force the ObjectStore to immediately clean out
     *                    all older transactions in order to synchronously compress the storage
     */
    void retainTx(OrderedSet<Int> inUseTxIds, Boolean force = False)
        {
        TODO
        }


    // ----- IO handling ---------------------------------------------------------------------------

    /**
     * Determine the files owned by this storage.
     *
     * @return an iterator over all of the files that are presumed to be owned by this storage
     */
    Iterator<File> findFiles()
        {
        return dataDir.files().filter(f -> f.name.endsWith(".json")).toArray().iterator();
        }

    /**
     * Initialize the state of the ObjectStore by scanning the persistent image of the ObjectStore's
     * data.
     *
     * @return True if no issues were detected (which does not guarantee that no issues exist);
     *         False indicates that fixes are required
     */
    Boolean quickScan()
        {
        model        = Empty;
        filesUsed    = 0;
        bytesUsed    = 0;
        lastAccessed = Null;
        lastModified = Null;

        Directory dir = dataDir;
        if (dir.exists)
            {
            lastAccessed = dir.accessed;
            lastModified = dir.modified;

            for (File file : findFiles())
                {
                ++filesUsed;
                bytesUsed   += file.size;
                lastAccessed = lastAccessed?.maxOf(file.accessed) : file.accessed;
                lastModified = lastModified?.maxOf(file.modified) : file.modified;
                }

            // knowledge of model categorization is owned by the ObjectStore sub-classes; this is
            // just an initial guess at this level; sub-classes should override this if there is a
            // more correct calculation
            model = switch (filesUsed)
                {
                case 0: Empty;
                case 1: Small;
                default: Medium;
                };
            }

        return True;
        }

    /**
     * Initialize the state of the ObjectStore by deep-scanning (and optionally fixing as necessary)
     * the persistent image of the ObjectStore's data.
     *
     * @param fix  (optional) specify True to fix the persistent data if necessary, and False to
     *             deep scan without modifying the persistent data even if an error is detected
     *
     * @return True if the scan is clean (which, if `fix` is True, indicates that the scan corrected
     *         any errors that it encountered)
     */
    Boolean deepScan(Boolean fix = True)
        {
        // knowledge of how to perform a deep scan is handled by specific ObjectStore sub-classes
        return quickScan();
        }

    /**
     * Log an error description.
     *
     * @param err  an error message to add to the log
     */
    void log(String err)
        {
        try
            {
            errs.add(err);
            }
        catch (Exception e) {}
        }


    // ----- Hashable funky interface --------------------------------------------------------------

    @Override static <CompileType extends ObjectStore> Int hashCode(CompileType value)
        {
        // use the hash code of the path; we are not expecting ObjectStore instances from multiple
        // different databases to end up in the same hashed data structure, so this should be more
        // than sufficient
        return value.info.path.hashCode();
        }

    @Override static <CompileType extends ObjectStore> Boolean equals(CompileType value1, CompileType value2)
        {
        // equality of ObjectStore references is very strict
        return &value1 == &value2;
        }
    }
