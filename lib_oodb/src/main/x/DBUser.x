/**
 * A database user or group.
 */
interface DBUser
    {
    /**
     * The perpetual-unique id of the DBUser within this database.
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
     * True iff this `DBUser` object represents a group of users, and not an individual user.
     */
    @RO Boolean group;

    /**
     * Groups that this user (or group) belongs to.
     */
    @RO Set<DBUser> groups;

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
     * @return `True` iff this `DBUser` is permitted to execute the specified request
     */
    Boolean isAllowed(Permission request);
    }
