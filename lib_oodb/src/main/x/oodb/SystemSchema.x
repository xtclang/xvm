import model.DBObjectInfo;

/**
 * The system "sys" schema that is always present under the root schema.
 *
 * Every Database automatically contains a `DBSchema` named "sys"; it is the database system's own
 * schema. (The top-level schema "sys" is a reserved name; it is an error to attempt to override,
 * replace, or augment the database system's schema.) A database implementation may expose as much
 * information as it desires via its schema, but there will always exist the following contents,
 * regardless of the database implementation:
 *
 * * `sys/info` - the [DBValue] containing the [DBInfo] value for this Database
 * * `sys/users` - a [DBMap] of user name to [DBUser]
 * * `sys/types` - a [DBList] of all distinct `Type` objects that are supported by the Database
 * * `sys/objects` - a [DBMap] of path to [DBObject]
 * * `sys/schemas` - a [DBMap] of path to [DBSchema]
 * * `sys/counters` - a [DBMap] of path to [DBCounter]
 * * `sys/values` - a [DBMap] of path to [DBValue] DBObjects
 * * `sys/maps` - a [DBMap] of path to [DBMap]
 * * `sys/lists` - a [DBMap] of path to [DBList]
 * * `sys/queues` - a [DBMap] of path to [DBQueue]
 * * `sys/processors` - a [DBMap] of path to [DBProcessor]
 * * `sys/logs` - a [DBMap] of path to [DBLog]
 * * `sys/pending` - a [DBList] ordered by scheduled invocation date/time of
 *   [Pending](DBProcessor.Pending) objects
 * * `sys/transactions` - a [DBLog] of [Transaction] objects
 * * `sys/errors` - a [DBLog] of errors REVIEW String
 */
interface SystemSchema
        extends DBSchema
    {
    /**
     * The [DBValue] containing the [DBInfo] value for this Database.
     */
    @RO DBValue<DBInfo> info;

    /**
     * A [DBMap] of user name to [DBUser].
     */
    @RO DBMap<String, DBUser> users;

    /**
     * A [DBMap] of all distinct `Type` objects that are supported by the Database, keyed by a
     * `String` that can be used to obtain the `Type` from the `TypeSystem` of the database.
     */
    @RO DBMap<String, Type> types;

    /**
     * A [DBMap] of path to a [DBObjectInfo] instance for each [DBObject].
     */
    @RO DBMap<String, DBObjectInfo> objects;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBSchema].
     */
    @RO DBMap<String, DBObjectInfo> schemas;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBCounter].
     */
    @RO DBMap<String, DBObjectInfo> counters;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBValue].
     */
    @RO DBMap<String, DBObjectInfo> values;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBMap].
     */
    @RO DBMap<String, DBObjectInfo> maps;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBList].
     */
    @RO DBMap<String, DBObjectInfo> lists;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBQueue].
     */
    @RO DBMap<String, DBObjectInfo> queues;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBProcessor].
     */
    @RO DBMap<String, DBObjectInfo> processors;

    /**
     * A Map of path to a [DBObjectInfo] instance for each [DBLog].
     */
    @RO DBMap<String, DBObjectInfo> logs;

    /**
     * A [DBList] ordered by scheduled invocation date/time of [Pending](DBProcessor.Pending)
     * objects. Note that a database may restrict access to the pending execution list for security
     * reasons.
     */
    @RO DBList<DBProcessor.Pending> pending;

    /**
     * A [DBLog] of [DBTransaction] objects. Note that a database may restrict access to the
     * transaction log for security reasons; also, a database may not retain a persistent
     * transaction log, so the log may only contain a small, recent set of entries. In other words,
     * this log may be useful for debugging, but its behavior must not be relied upon for
     * application purposes.
     */
    @RO DBLog<DBTransaction> transactions;

    /**
     * A [DBLog] of database errors.  Note that a database may restrict access to the error log for
     * security reasons. In other words, log this may be useful for debugging, but its behavior must
     * not be relied upon for application purposes.
     */
    @RO DBLog<String> errors;
    }