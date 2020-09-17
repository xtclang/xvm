/**
 * The system "db" schema that is always present under the root schema.
 *
 * Every Database automatically contains a `DBSchema` named "db"; it is the database system's own
 * schema. (The top-level schema "db" is a reserved name; it is an error to attempt to override,
 * replace, or augment the database system's schema.) A database implementation may expose as much
 * information as it desires via its schema, but there will always exist the following contents,
 * regardless of the database implementation:
 *
 * * `db/info` - the [DBInfo] singleton for this Database
 * * `db/users` - a [DBMap] of user name to [DBUser]
 * * `db/types` - a [DBList] of all distinct `Type` objects that are supported by the Database
 * * `db/objects` - a [DBMap] of name (path) to [DBObject]
 * * `db/schemas` - a [DBMap] of name (path) to [DBSchema]
 * * `db/maps` - a [DBMap] of name (path) to [DBMap]
 * * `db/queues` - a [DBMap] of name (path) to [DBQueue]
 * * `db/lists` - a [DBMap] of name (path) to [DBList]
 * * `db/logs` - a [DBMap] of name (path) to [DBLog]
 * * `db/counters` - a [DBMap] of name (path) to [DBCounter]
 * * `db/singletons` - a [DBMap] of name (path) to singleton DBObjects
 * * `db/functions` - a [DBMap] of name (path) to [DBFunction]
 * * `db/pending` - a [DBList] ordered by scheduled invocation date/time of [DBInvoke] objects
 * * `db/transactions` - a [DBLog] of [Transaction] objects
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