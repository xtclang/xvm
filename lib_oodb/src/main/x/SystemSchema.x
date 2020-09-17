/**
 * The system "sys" schema that is always present under the root schema.
 *
 * Every Database automatically contains a `DBSchema` named "sys"; it is the database system's own
 * schema. (The top-level schema "sys" is a reserved name; it is an error to attempt to override,
 * replace, or augment the database system's schema.) A database implementation may expose as much
 * information as it desires via its schema, but there will always exist the following contents,
 * regardless of the database implementation:
 *
 * * `sys/info` - the [DBInfo] singleton for this Database
 * * `sys/users` - a [DBMap] of user name to [DBUser]
 * * `sys/types` - a [DBList] of all distinct `Type` objects that are supported by the Database
 * * `sys/objects` - a [DBMap] of name (path) to [DBObject]
 * * `sys/schemas` - a [DBMap] of name (path) to [DBSchema]
 * * `sys/maps` - a [DBMap] of name (path) to [DBMap]
 * * `sys/queues` - a [DBMap] of name (path) to [DBQueue]
 * * `sys/lists` - a [DBMap] of name (path) to [DBList]
 * * `sys/logs` - a [DBMap] of name (path) to [DBLog]
 * * `sys/counters` - a [DBMap] of name (path) to [DBCounter]
 * * `sys/singletons` - a [DBMap] of name (path) to singleton DBObjects
 * * `sys/functions` - a [DBMap] of name (path) to [DBFunction]
 * * `sys/pending` - a [DBList] ordered by scheduled invocation date/time of [DBInvoke] objects
 * * `sys/transactions` - a [DBLog] of [Transaction] objects
 */
interface SystemSchema
        extends DBSchema
    {
    /**
     * The [DBInfo] singleton for this Database.
     */
    @RO DBInfo info;

    /**
     * A [DBMap] of user name to [DBUser].
     */
    @RO DBMap<String, DBUser> users;

    /**
     * A [DBList] of all distinct `Type` objects that are supported by the Database.
     */
    @RO DBList<Type> types;

    /**
     * A [DBMap] of name (path) to [DBObject].
     */
    @RO DBMap<String, DBObject> objects;

    /**
     * A [DBMap] of name (path) to [DBSchema].
     */
    @RO DBMap<String, DBSchema> schemas;

    /**
     * A [DBMap] of name (path) to [DBMap].
     */
    @RO DBMap<String, DBMap> maps;

    /**
     * A [DBMap] of name (path) to [DBQueue].
     */
    @RO DBMap<String, DBQueue> queues;

    /**
     * A [DBMap] of name (path) to [DBList].
     */
    @RO DBMap<String, DBList> lists;

    /**
     * A [DBMap] of name (path) to [DBLog].
     */
    @RO DBMap<String, DBLog> logs;

    /**
     * A [DBMap] of name (path) to [DBCounter].
     */
    @RO DBMap<String, DBCounter> counters;

    /**
     * A [DBMap] of name (path) to [DBSingleton].
     */
    @RO DBMap<String, DBSingleton> singletons;

    /**
     * A [DBMap] of name (path) to [DBFunction].
     */
    @RO DBMap<String, DBFunction> functions;

    /**
     * A [DBList] ordered by scheduled invocation date/time of [DBInvoke] objects.
     */
    @RO DBList<DBInvoke> pending;

    /**
     * A [DBLog] of [Transaction] objects.
     */
    @RO DBLog<Transaction> transactions;
    }