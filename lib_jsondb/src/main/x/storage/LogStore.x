/**
 * A log storage API.
 */
interface LogStore<Value extends immutable Const>
    {
    /**
     * Append the specified value to the log.
     *
     * @param txId   the "write" transaction identifier
     * @param value  the value to append
     */
    void append(Int txId, Value value);
    }