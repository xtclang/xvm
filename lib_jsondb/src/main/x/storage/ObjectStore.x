import Catalog.Status;
import model.DBObjectInfo;
import oodb.DBObject.DBCategory as Category;

/**
 * This is an abstract base class for storage services that manage information as JSON formatted
 * data on disk. The abstraction is also designed to allow support for caching optimizations to be
 * layered on top of a persistent storage implementation -- probably more to avoid expensive
 * serialization and deserialization, than to avoid raw I/O operations (which are expected to be
 * low-cost in NVMe-flash an SAN environments). as anything.   Each DBObject has a coryresponding
 * ObjectStore that manages its
 *
  ObjectStorage represents the mapping
 * between binary storage on disk using files and directories, and the fundamental set of operations
 * that a particular DBObject interface (such as [DBMap] or [DBList]) requires and uses to provide
 * its high-level functionality.
 *
 * When an ObjectStore is constructed, it is an inert mode; it does not interact at all with the
 * disk storage until it is explicitly instructed to do so. Normally, the ObjectStore assumes that
 * the storage is intact, and it takes a fast-path to initialize its information about the disk
 * contents; this is called [quickScan]. Recovery of the storage uses a [deepScan], and may be
 * destructive.
 *
 * TODO hierarchical deletion (recursive)
 * TODO background maintenance
 */
service ObjectStore(Catalog catalog, DBObjectInfo info, Appender<String> errs)
        implements Closeable
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Catalog that this ObjectStore belongs to.
     */
    public/protected Catalog catalog;

    /**
     * The DBObjectInfo that identifies the configuration of this ObjectStore.
     */
    public/protected DBObjectInfo info;

    /**
     * The id of the database object for which this storage exists.
     */
    Int id.get()
        {
        return info.id;
        }

    /**
     * The `DBCategory` of the `DBObject`.
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
     * The current status of this ObjectStore.
     */
    public/protected Status status = Closed;

    /**
     * True iff the ObjectStore is permitted to write to persistent storage.
     */
    public/protected Boolean writeable = False;

    /**
     * The path that specifies this `DBObject`, and that indicates the storage location for the
     * data contained within it.
     */
    String path.get()
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
        Directory dir = catalog.dir;
        loop: for (String part : path.split('/'))
            {
            if (!loop.last)
                {
                dir = dir.dirFor(part);
                }
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
     * The most recent transaction identifier for which this ObjectStore processed data changes.
     */
    Int? newestTx = Null;

    /**
     * Count of the number of operations since the ObjectStore was created.
     */
    Int opCount = 0;

    /**
     * Determine if this ObjectStore for a DBObject is allowed to write to disk. True iff the
     * catalog is not read only and the DBObject has not been deprecated or removed.
     */
    protected Boolean defaultWriteable.get()
        {
        return !catalog.readOnly && info.id > 0 && info.lifeCycle == Current;
        }


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
            default:
                assert;
            }

        status    = Closed;
        writeable = False;
        }

    /**
     * Delete the contents of the ObjectStore's persistent data.
     */
    void delete()
        {
        @Inject Clock clock;

        model        = Empty;
        filesUsed    = 0;
        bytesUsed    = 0;
        lastAccessed = Null;
        lastModified = Null;

        Directory dir = dataDir;
        if (dir.exists)
            {
            // if it's a directory, delete all .json contents, and if it's empty, delete the dir
            // REVIEW
            for (File file : findFiles())
                {
                file.delete();
                }

            if (!dir.filesRecursively().next())
                {
                dir.deleteRecursively();
                }
            }
        }


    // ----- IO handling ---------------------------------------------------------------------------

    /**
     * Determine the files owned by this storage.
     *
     * @return an iterator over all of the files that are presumed to be owned by this storage
     */
    Iterator<File> findFiles()
        {
        // TODO GG shouldn't type inference support this construct: return [].iterator();
        return dataDir.files().filter(f -> f.name.endsWith(".json")).toArray().iterator();
        }

    /**
     * Initialize the state of the ObjectStore by scanning the persistent image of the ObjectStore's
     * data.
     *
     * @return True if the scan is clean; false if fixes are required
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
            // just an initial guess at this level
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
        // knowledge of how to perform a deep scan is owned by the ObjectStore sub-classes
        return quickScan();
        }

    /**
     * Clean up the persistent storage, releasing any data older than the specified transaction.
     * This operation can be asynchronous; it simply indicates that the storage is allowed to forget
     * older data.
     *
     * @param tx     the oldest transaction whose information needs to be retained
     * @param force  (optional) specify True to force the ObjectStore to immediately clean out all
     *               older transactions in order to synchronously compress the storage
     */
    void forgetOlderThan(Int tx, Boolean force = False)
        {
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
    }
