/**
 * The representation of a a pending `DBProcessor` execution in the database.
 *
 * By representing an invocation as data:
 *
 * * The information can be communicated over a network connection;
 *
 * * A record of various invocations can be collected for later examination; and
 *
 * * Desired future invocations can be stored in persistent storage to ensure that the request for
 *   their execution can survive database outage or other events.
 */
interface DBPending<Element extends immutable Const>
    {
    /**
     * The path of the `DBProcessor`.
     */
    @RO Path processor;

    /**
     * The `Element` to be processed.
     */
    @RO Element element;

    /**
     * Determine whether the invocation is explicitly scheduled, and when it is scheduled for.
     *
     * @return True if the invocation is scheduled to run at a specific point; False if the
     *         invocation needs to be executed immediately
     * @return (conditional) the scheduled `DateTime`, or the daily scheduled `Time` to execute
     */
    conditional DateTime | Time isScheduled();

    /**
     * Determine if the pending invocation is auto-rescheduling (i.e. repeating).
     *
     * @return True if the invocation is auto-rescheduling, aka "repeating".
     * @return (conditional) the interval of repeating
     * @return (conditional) the policy of repeating when the previous execution has not already
     *         completed
     */
    conditional (Duration repeatInterval, DBProcessor.Repeatable.Policy repeatPolicy) isRepeating();

    /**
     * Determine the priority of the pending execution.
     */
    @RO DBProcessor.Prioritizable.Priority priority;

    /**
     * The number of times that this pending invocation has already been attempted, and has failed.
     */
    @RO Int previousFailures;
    }
