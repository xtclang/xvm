/**
 * A value storage API.
 */
interface ValueStore<Value extends immutable Const>
    {
    /**
     * Obtain the single value as it existed immediately after the specified transaction finished
     * committing, or as it exists within the transaction (if it has not yet committed).
     *
     * @param txId  specifies the transaction identifier to use to determine the point-in-time data
     *              stored in the database, as if the value of the singleton were read immediately
     *              after that specified transaction had committed
     *
     * @return the single value as of the specified transaction
     */
    Value load(Int txId);

    /**
     * Modify the single value as part of the specified transaction by replacing the value.
     *
     * @param txId   the "write" transaction identifier
     * @param value  the new single value
     */
    void store(Int txId, Value value);
    }