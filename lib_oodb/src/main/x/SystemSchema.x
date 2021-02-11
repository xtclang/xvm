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
 * * `sys/maps` - a [DBMap] of path to [DBMap]
 * * `sys/queues` - a [DBMap] of path to [DBQueue]
 * * `sys/lists` - a [DBMap] of path to [DBList]
 * * `sys/logs` - a [DBMap] of path to [DBLog]
 * * `sys/counters` - a [DBMap] of path to [DBCounter]
 * * `sys/values` - a [DBMap] of path to [DBValue] DBObjects
 * * `sys/functions` - a [DBMap] of path to [DBFunction]
 * * `sys/pending` - a [DBList] ordered by scheduled invocation date/time of [DBInvoke] objects
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
     * A [DBMap] of path to [DBObject].
     */
    @RO DBMap<String, DBObject> objects;

    /**
     * A [DBMap] of path to [DBSchema].
     */
    @RO DBMap<String, DBSchema> schemas;

    /**
     * A [DBMap] of path to [DBMap].
     */
    @RO DBMap<String, DBMap> maps;

    /**
     * A [DBMap] of path to [DBQueue].
     */
    @RO DBMap<String, DBQueue> queues;

    /**
     * A [DBMap] of path to [DBList].
     */
    @RO DBMap<String, DBList> lists;

    /**
     * A [DBMap] of path to [DBLog].
     */
    @RO DBMap<String, DBLog> logs;

    /**
     * A [DBMap] of path to [DBCounter].
     */
    @RO DBMap<String, DBCounter> counters;

    /**
     * A [DBMap] of path to [DBValue].
     */
    @RO DBMap<String, DBValue> values;

    /**
     * A [DBMap] of path to [DBFunction].
     */
    @RO DBMap<String, DBFunction> functions;

    /**
     * A [DBList] ordered by scheduled invocation date/time of [DBInvoke] objects. Note that a
     * database may restrict access to the pending execution list for security reasons.
     */
    @RO DBList<DBInvoke> pending;

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