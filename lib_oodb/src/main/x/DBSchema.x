/**
 * A `DBSchema` is a `DBObject` that is used to hierarchically organize database contents.
 *
 * Every Database automatically contains a `DBSchema` named "db"; it is the database system's own
 * schema. (The top-level schema "db" is a reserved name; it is an error to attempt to override,
 * replace, or augment the database system's schema.) A database implementation may expose as much
 * information as it desires via its schema, but there will always exist the following contents,
 * regardless of the database implementation:
 *
 * * `db/info` - the [Database.Info] singleton for this Database
 * * `db/users` - a [DBMap] of user name to [Database.User]
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
interface DBSchema
        extends DBObject
    {
    @Override
    @RO Boolean transactional.get()
        {
        return False;
        }
    }