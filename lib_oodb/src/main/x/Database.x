/**
 * A database.
 */
interface Database
        extends DBSchema
    {
    /**
     * The meta information about the database.
     */
    @RO Info info;

    /**
     * A simple set of meta-data describing a database.
     */
    static interface Info
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

    /**
     * The current database [User].
     */
    @RO User user;

    /**
     * A database user or group.
     */
    static interface User
        {
        /**
         * The perpetual-unique id of the User within this database.
         */
        @RO UInt id;

        /**
         * The temporal-unique user or group name.
         */
        @RO String name;

        /**
         * True if this user is active.
         */
        @RO Boolean active;

        /**
         * True iff this `User` object represents a group of users, and not an individual user.
         */
        @RO Boolean group;

        /**
         * Groups that this user (or group) belongs to.
         */
        @RO Set<User> groups;

        /**
         * Explicit permissions for this user.
         */
        @RO Set<Permission> permissions;

        /**
         * Explicit permission revocations for this user.
         */
        @RO Set<Permission> revocations;

        /**
         * Determine whether the specified request is allowed to be made by this user.
         *
         * @param request  the [Permission] to test for
         *
         * @return `True` iff this `User` is permitted to execute the specified request
         */
        Boolean isAllowed(Permission request);
        }

    /**
     * A `Permission` represents a targeted action that can be allowed (permitted) or disallowed
     * (revoked).
     */
    static const Permission(String target, String action);

    /**
     * A wild-card representing all targets (database objects) that a permission may apply to.
     */
    static String AllTargets = "*";

    /**
     * A wild-card representing all actions (database functions) that a permission may apply to.
     */
    static String AllActions = "*";

    import Transaction.Priority;

    /**
     * Create a new transaction.
     *
     * @param timeout     the requested time-out, which allows the database to roll back and discard
     *                    the transaction after that period of time has elapsed
     * @param name        a descriptive name to associate with the transaction
     * @param id          an integer identifier to associate with the transaction
     * @param priority    the transactional priority
     * @param retryCount  the number of times that this same transaction has already been attempted
     *
     * @return the [Transaction] object
     *
     * @throws IllegalState  if a Transaction already exists
     */
    Transaction createTransaction(Duration? timeout     = Null,
                                  String?   name        = Null,
                                  UInt?     id          = Null,
                                  Priority  priority    = Normal,
                                  Int       retryCount  = 0);

    /**
     * Determine if there is already a transaction, and if there is, obtain it.
     *
     * @return True iff there is an existing transaction
     * @return (conditional) the [Transaction] object
     */
    conditional Transaction currentTransaction();

    /**
     * Obtain the existing transaction, or create one if one does not already exist.
     *
     * @param timeout     the requested time-out, which allows the database to roll back and discard
     *                    the transaction after that period of time has elapsed
     * @param name        a descriptive name to associate with the transaction
     * @param id          an integer identifier to associate with the transaction
     * @param priority    the transactional priority
     * @param retryCount  the number of times that this same transaction has already been attempted
     *
     * @return the [Transaction] object
     */
    Transaction ensureTransaction(Duration? timeout     = Null,
                                  String?   name        = Null,
                                  UInt?     id          = Null,
                                  Priority  priority    = Normal,
                                  Int       retryCount  = 0)
        {
        if (Transaction tx := currentTransaction())
            {
            return tx;
            }

        return createTransaction(timeout, name, id, priority, retryCount);
        }
    }
