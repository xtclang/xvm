/**
 * A simple set of meta-data describing a database.
 */
interface DBInfo
        extends DBSingleton
    {
    /**
     * The name of the database.
     */
    @RO String name;

    /**
     * The current version of the database.
     */
    @RO Version version;

    /**
     * The date/time at which the database was created. The value has no meaning for a database
     * that does not retain the creation time.
     */
    @RO DateTime created;

    /**
     * The date/time at which the database was last modified. The value has no meaning for a
     * database that does not retain the last modification time.
     */
    @RO DateTime modified;

    /**
     * The date/time at which data in the database was last accessed. The value has no meaning
     * for a database that does not retain the last access time.
     */
    @RO DateTime accessed;

    /**
     * True iff the database is readable.
     */
    @RO Boolean readable;

    /**
     * True iff the database is writable.
     */
    @RO Boolean writable;

    /**
     * The number of bytes of storage consumed by the database. This value may be an estimate,
     * or an actual value may not be available.
     */
    @RO Int size;
    }
