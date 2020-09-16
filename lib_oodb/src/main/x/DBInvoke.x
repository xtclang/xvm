/**
 * The representation of a a pending (or previous) function invocation in the database. By
 * representing an invocation as data:
 *
 * * The information can be communicated over a network connection;
 * * A record of various invocations can be collected for later examination; and
 * * Desired future invocations can be stored in persistent storage to ensure that the request for
 *   their execution can survive database outage or other events.
 */
interface DBInvoke<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
    {
    /**
     * The `DateTime` at which the invocation should occur; `Null` indicates that the invocation
     * should occur as soon as possible.
     */
    @RO DateTime? when;

    /**
     * The database function to invoke.
     */
    @RO DBFunction<ParamTypes, ReturnTypes> dbFunction;

    /**
     * The parameters for the invocation.
     */
    @RO Tuple<ParamTypes> params;
    }
