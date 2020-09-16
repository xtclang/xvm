/**
 * The representation for a database "function", used for each procedure that can execute within the
 * database. By explicitly defining these procedures in the database schema, and by representing
 * them as data:
 *
 * * The invocation of these procedures can also be expressed as data, for example, data that
 *   travels over a network connection;
 * * A record of various invocations can be collected for later examination; and
 * * Desired future invocations can be stored in persistent storage to ensure that the request for
 *   their execution can survive database outage or other events.
 *
 * A function is a _terminal_ (or _leaf_) `DBObject`; it does not _contain_ other `DBObject`
 * instances.
 */
interface DBFunction<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends DBObject
    {
    /**
     * The callable function that can be used to invoke this database procedure.
     */
    @RO Function<ParamTypes, ReturnTypes> callable;
    }
