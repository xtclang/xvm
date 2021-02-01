/**
 * The database interface for managing a single, _standalone_ object value. This does _not_ refer to
 * a value that is inside a map, list, or some other container; rather, it refers to a value that is
 * managed as-is _instead_ of storing the value inside of a map, list, or some other container.
 *
 * The `DBValue` is a `DBObject` in the database that contains a single value. The single value can
 * be obtained or replaced. However, it always exists, so it is neither "created" nor can it be
 * "removed"; if the value must be _optional_, then declare the `Value` type as nullable:
 *
 *     DBValue<Person?> admin;
 */
interface DBValue<Value extends immutable Const>
        extends DBObject
    {
    /**
     * Obtain the value.
     *
     * @return the value held by the `DBValue`
     */
    Value get();

    /**
     * Modify the value.
     *
     * @param value  the new value
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    void set(Value value)
        {
        throw new ReadOnly($"The value at \"{dbPath}\" cannot be modified");
        }


    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBValue;
        }

    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents specific database changes that occurred to a transactional database value.
     */
    static interface DBChange<Value>
        {
        /**
         * True iff the counter was read during the transaction.
         */
        @RO Value oldValue;

        /**
         * True iff the counter was written during the transaction.
         */
        @RO Value newValue;
        }

    /**
     * Represents a transactional change to a database `DBValue`.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the value modified in the transaction.
     */
    @Override
    interface TxChange
            extends DBChange<Value>
        {
        }


    // ----- transaction trigger API ---------------------------------------------------------------

    /**
     * Represents an automatic response to a change that occurs when a transaction commits.
     *
     * This interface can be used in lieu of the more generic [DBObject.Trigger] interface, but it
     * exists only as a convenience, in that it can save the application developer a few type-casts
     * that might otherwise be necessary.
     */
    @Override
    static interface Trigger<TxChange extends DBValue.TxChange>
            extends DBObject.Trigger<TxChange>
        {
        }
    }
