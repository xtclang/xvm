/**
 * A database singleton object is a `DBObject` in the database that contains a single value; in
 * other words, it is not held in a complex container such as a `DBList` or a `DBMap`. Instead, the
 * singleton container contains a single value, which can be obtained or replaced.
 */
interface DBSingleton<Value extends immutable Const>
        extends DBObject
    {
    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }

    /**
     * Obtain the singleton value.
     *
     * @return the value of the singleton
     */
    Value get();

    /**
     * Modify the singleton by replacing the value.
     *
     * @param value  the new value for the singleton
     */
    void set(Value value);
    }
