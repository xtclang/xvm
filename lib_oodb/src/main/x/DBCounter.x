/**
 * A database counter is a specialized form of a `DBValue` that is used to generate unique keys
 * or to count things in a highly concurrent manner. Unlike most database objects, a counter can be
 * used in an _extra-transactional_ manner, which is extremely useful for unique id (e.g. sequential
 * key) generation. When used correctly, a `DBCounter` can support extremely high levels of
 * concurrency by avoiding mixing reads and writes:
 *
 * * When a transactional `DBCounter` is used for read purposes, it does not block any other
 *   transactions that are reading xor writing the counter value;
 *
 * * When a transactional `DBCounter` is updated using a "blind" update (such as the [increment] and
 *   [decrement] methods), it does not block any other transactions that are similarly doing "blind"
 *   updates of the counter value, nor does it block any readers, because these updates are
 *   _composable_;
 *
 * * An extra-transactional `DBCounter` is designed to support high concurrency when generating new
 *   identities via the [next] method.
 *
 * In the case of an extra-transactional `DBCounter`, the database ensures that no two requests for
 * a [next] id will return the same value, regardless of whether they are in the same or separate
 * transactions, or even executing on different machines or in different data-centers. The counter
 * is stored persistently, such that the provided values will never be repeated for the lifetime of
 * the database, assuming the absence of unnatural events, such as a database restore from a backup
 * (or the counter value being explicitly set, e.g. to a previous value). Since this use of the
 * counter is a monotonically increasing function, it is possible for "holes" to appear in the
 * sequence of id's obtained from the counter for a variety of reasons. The Counter does not attempt
 * to prevent, identify, or reclaim these "holes". (A small exception to the "monotonically
 * increasing" rule is permitted to exist temporarily in a concurrent environment, such as in a
 * multi-threaded or distributed environment, in order to support more efficient concurrent
 * execution.)
 */
interface DBCounter
        extends DBValue<Int>
    {
    // ----- DBValue methods -----------------------------------------------------------------------

    /**
     * Obtain the counter value without modifying the counter.
     *
     * For a transactional counter, calling this method will negatively impact concurrency if the
     * transaction also modifies the counter (or if the counter is otherwise enlisted into the
     * transaction).
     *
     * For an extra-transactional counter, calling this method will produce a value that is
     * inherently unreliable in the presence of concurrency or distribution; because the value is
     * not auto-incremented, the caller must not use the returned value as an identity.
     *
     * @return the integer value of the counter
     */
    @Override
    Int get();

    /**
     * Modify the counter value without obtaining the counter.
     *
     * For a transactional counter, calling this method will negatively impact concurrency; it is
     * far preferable to use the [increment], [decrement], or [adjustBy] methods.
     *
     * For an extra-transactional counter, this method is inherently unreliable in the presence of
     * concurrency or distribution.
     *
     * @param value  the new value for the counter
     *
     * @throws Exception if the implementation rejects the value for any reason
     */
    @Override
    void set(Int value);


    // ----- key counter support -------------------------------------------------------------------

    /**
     * Generate the next counter value. Optionally, produce `count` values. This method is designed
     * to be used on an extra-transactional `DBCounter` for key generation; on a transactional
     * counter, this method will negatively impact concurrency.
     *
     * @param count  the number of sequential values to generate, `count > 0`
     *
     * @return the first generated value, which is the old value of the counter
     */
    Int next(Int count = 1)
        {
        assert count >= 1;
        Int oldValue = get();
        Int newValue = oldValue + count;
        set(newValue);
        return oldValue;
        }


    // ----- blind updates -------------------------------------------------------------------------

    /**
     * Modify the counter value _in a relative manner_ without obtaining the counter. This method is
     * a "blind update", which is designed to maximize concurrency by being composable with other
     * blind updates.
     *
     * @param value  the relative value adjustment to make to the counter
     */
    void adjustBy(Int value);

    /**
     * Increment the count. This method is a "blind update", which is designed to maximize
     * concurrency by being composable with other blind updates.
     */
    void increment()
        {
        adjustBy(1);
        }

    /**
     * Decrement the count. This method is a "blind update", which is designed to maximize
     * concurrency by being composable with other blind updates.
     */
    void decrement()
        {
        adjustBy(-1);
        }


    // ----- read plus update operations -----------------------------------------------------------

    /**
     * Increment the count and return the new incremented counter value. This method will negatively
     * impact concurrency for a transactional counter, because it both reads and writes the counter
     * value, which makes it non-composable.
     *
     * @return the counter value after the increment
     */
    Int preIncrement()
        {
        increment();
        return get();
        }

    /**
     * Decrement the count and return the new decremented counter value. This method will negatively
     * impact concurrency for a transactional counter, because it both reads and writes the counter
     * value, which makes it non-composable.
     *
     * @return the counter value after the decrement
     */
    Int preDecrement()
        {
        decrement();
        return get();
        }

    /**
     * Increment the count and return the counter value from before the increment. This method will
     * negatively impact concurrency for a transactional counter, because it both reads and writes
     * the counter value, which makes it non-composable.
     *
     * @return the counter value before the increment
     */
    Int postIncrement()
        {
        Int result = get();
        increment();
        return result;
        }

    /**
     * Decrement the count and return the counter value from before the decrement. This method will
     * negatively impact concurrency for a transactional counter, because it both reads and writes
     * the counter value, which makes it non-composable.
     *
     * @return the counter value before the decrement
     */
    Int postDecrement()
        {
        Int result = get();
        decrement();
        return result;
        }


    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBCounter;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents specific database changes that occurred to a transactional database counter.
     */
    @Override
    static interface DBChange
            extends DBValue.DBChange<Int>
        {
        /**
         * True iff the change represents a counter modification that was able to avoid reading the
         * previous value. In theory, a change to a counter may be possible without reading the
         * original value, by making the adjustment into a relative adjustment, and thus making the
         * transaction more easily composable with other transactions (e.g. for purposes of
         * re-ordering and/or reducing the frequency of roll-backs).
         *
         * Note that if `relativeOnly` is true, that the `oldValue` and `newValue` may not be known
         * until the point that the transaction commits.
         */
        @RO Boolean relativeOnly;

        /**
         * The amount that the counter was adjusted by, during the transaction. In theory, a change
         * to a counter may not have the "before" and "after" values, and instead may only have the
         * size of the adjustment (which allows the transaction to be composed with, and re-ordered
         * vis-a-vis other similar transactions).
         */
        @RO Int adjustment.get()
            {
            return newValue - oldValue;
            }
        }

    /**
     * Represents a transactional change to a database counter.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the counter modified in the transaction.
     */
    @Override
    interface TxChange
            extends DBChange
        {
        }


    // ----- transaction trigger API ---------------------------------------------------------------

    // these interfaces can be used in lieu of the more generic interfaces of the same names found
    // on [DBObject], but these exists only as a convenience, in that they can save the application
    // database developer a few type-casts that might otherwise be necessary.

    @Override static interface Validator<TxChange extends DBCounter.TxChange>
            extends DBObject.Validator<TxChange> {}
    @Override static interface Rectifier<TxChange extends DBCounter.TxChange>
            extends DBObject.Rectifier<TxChange> {}
    @Override static interface Distributor<TxChange extends DBCounter.TxChange>
            extends DBObject.Distributor<TxChange> {}
    }
