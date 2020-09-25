import ecstasy.io.ByteArrayOutputStream;
import ecstasy.io.PackedDataOutput;

/**
 * Metadata catalog for a database.
 *
 * TODO version - should only be able to open the catalog with the correct TypeSystem version
 */
service Catalog
        implements Closeable
    {
    /**
     * The status of this `Catalog`.
     *
     * * `Initial` - The `Catalog` has been configured with a directory that does not contain a
     *   catalog.
     * * `Configuring` - This `Catalog` object has the database open for schema definition and
     *   modification, or other maintenance work.
     * * `Running` - This `Catalog` object has the database open for data access.
     * * `LockedOut` - This `Catalog` object detected the _possibility_ that the directory has
     *   already been opened by another `Catalog` instance, and is currently in use.
     * * `Recovering` - This `Catalog` object has been instructed to recover the database.
     * * `Closed` - This `Catalog` object has shut down, and is no longer usable.
     */
    enum Status {Empty, Configuring, Running, LockedOut, Recovering, Closed}

    /**
     * Open the catalog for the specified directory.
     *
     * @param dir       the directory that contains (or may contain) the catalog
     * @param readOnly  pass `True` to access the catalog in a read-only manner
     */
    construct(Directory dir, Boolean readOnly = False)
        {
        assert:arg dir.exists;
        assert:arg dir.readable && (readOnly || dir.writable);

        this.dir      = dir;
        this.readOnly = readOnly;
        }
    finally
        {
        status = inferStatus();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The directory used to store the contents of the database
     */
    public/private Directory dir;

    /**
     * True iff the database was opened in read-only mode.
     */
    public/private Boolean readOnly = False;

    /**
     * The status of this `Catalog` object.
     */
    @Atomic public/private Status status;


    // ----- status management ---------------------------------------------------------------------

    /**
     * For a `Catalog` that is `Empty`, initialize the directory and file structures so that a
     * catalog exists in the previously specified directory. After creation, the `Catalog` will be
     * in the `Configuring` status, allowing the caller to populate the database schema.
     *
     * @param name  the name of the database to create
     *
     * @throws IllegalState  if the Catalog is not `Empty`, or is read-only
     */
    void create(String name)
        {
        transition(Empty, Configuring);

        TODO
        }

    /**
     * For an existent database, if this `Catalog` is `Running` or `Closed`, then transition to the
     * `Configuring` state, allowing modifications to be made to the database structure.
     *
     * @throws IllegalState  if the Catalog is not `Closed` or `Running`, or is read-only
     */
    void edit()
        {
        transition([Running, Closed], Configuring);

        TODO
        }

    /**
     * For an existent database, if this `Catalog` is `LockedOut`, then assume that the previous
     * owner terminated, take ownership of the database and verify its integrity, resulting in a
     * `Running` database.
     *
     * @throws IllegalState  if the Catalog is not `LockedOut`
     */
    void recover()
        {
        transition(LockedOut, Recovering, False);

        TODO

        transition(Recovering, Running, False);
        }

    /**
     * For an existent database, if this `Catalog` is `Configuring` or `Closed`, then transition to
     * the `Running` state, allowing modifications to be made to the database structure.
     *
     * @throws IllegalState  if the Catalog is not `Closed` or `Configuring`
     */
    void open()
        {
        transition([Configuring, Closed], Running, False);

        TODO
        }

    /**
     * For a `Catalog` that is `Empty`, initialize the directory and file structures so that a
     * catalog exists in the provided directory.
     */
    @Override
    void close()
        {
        switch (status)
            {
            case Empty:
                // nothing to close
                break;

            case Configuring:
                transition(Configuring, Closed);
                break;

            case Running:
                transition(Running, Closed);
                break;

            case LockedOut:
                // this Catalog object never took ownership of the database
                break;

            case Recovering:
                // this is bad; we were recovering the database, and yet here we are processing
                // something while the database is still not finished recovering; the recovery
                // failed with an exception that was caught, and the database is being shut down
                // without being recovered; just leave the persistent status as is
                break;

            case Closed:
                // already closed
                break;

            default:
                assert;
            }
        }

    /**
     * For a `Catalog` that is `Configuring`, initialize the directory and file structures so that a
     * catalog exists in the provided directory.
     *
     * @throws IllegalState  if the Catalog is not `Configuring`, or is read-only
     */
    void delete()
        {
        transition([Configuring, Closed], Running, performAction = () ->
            {
            TODO
            });
        }

    /**
     * Validate that the current status matches the required status, optionally verify that the
     * Catalog is not read-only, and then with a lock in place, verify that the disk image also
     * matches that assumption. While holding that lock, optionally perform an operation, and then
     * update the status to the specified ,  (and the cor
     *
     * @param requiredStatus  one or more valid starting `Status` values
     * @param requiresWrite   `True` iff the Catalog is not allowed to be read-only
     * @param targetStatus    the ending `Status` to transition to
     * @param performAction   a function to execute while the lock is held
     */
    protected void transition(Status | Status[] requiredStatus,
                              Status            targetStatus,
                              Boolean           requiresWrite = True,
                              function void()?  performAction = Null)
        {
        if (requiredStatus.is(Status))
            {
            assert status == requiredStatus;
            }
        else
            {
            assert requiredStatus.contains(status);
            }

        assert !(readOnly && requiresWrite);

        TODO

        status = targetStatus;
        }


    // ----- catalog lock and status file management -----------------------------------------------

    /**
     * The file used to indicate a short-term lock.
     */
    @Lazy File lockFile.calc()
        {
        return dir.fileFor("sys.lock");
        }

    /**
     * The file used to store the "in-use" status for the database.
     */
    @Lazy File statusFile.calc()
        {
        return dir.fileFor("sys.json");
        }

    @Lazy Directory sysDir.calc()
        {
        return dir.dirFor("sys");
        }

    /**
     * Without locking, examine the `Catalog` directory to determine what the status of the catalog
     * is.
     *
     * @return the apparent `Status`
     */
    protected Status inferStatus()
        {
        if (statusFile.exists)
            {
            TODO
            }
        else
            {
            return Empty;
            }
        }
//
//    protected conditional Closeable tryLock()
//        {
//        if (lockFile.create())
//            {
//            // verify the status file
//            TODO
//
//            @Inject Clock clock;
//            val raw = new @PackedDataOutput ByteArrayOutputStream();
//            DataOutput.writeAsciiStringZ(raw, clock.now.toString());
//            lockFile.contents = raw.bytes;
//
//            return True, () -> {lockFile.delete(); return;};
//            }
//
//        return False;
//        }
    }
