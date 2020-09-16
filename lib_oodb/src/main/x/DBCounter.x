/**
 * A database counter is used to count things in a highly concurrent manner. Unlike most database
 * objects, a counter can be used in an _extra-transactional_ manner, which is extremely useful for
 * unique id (e.g. sequential key) generation. When used correctly, a `DBCounter` can support
 * extremely high levels of concurrency by avoiding mixing reads and writes:
 *
 * * When a transactional `DBCounter` is used for read purposes, it does not block any other
 *   transactions that are reading xor writing the counter value;
 * * When a transactional `DBCounter` is updated using a "blind" update (such as the [increment] and
 *   [decrement] methods), it does not block any other transactions that are similarly doing "blind"
 *   updates of the counter value, nor does it block any readers, because these updates are
 *   _composable_;
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
        extends DBObject
    {
    /**
     * Obtain the counter value without modifying the counter.
     *
     * For a transactional counter, calling this method will negatively impact concurrency if the
     * transaction also modifies the counter (or if the counter is otherwise enlisted into the
     * transaction).
     *
     * For an extra-transactional counter, calling this method will produce a value that is
     * inherently unreliable in the presence of concurrency or distribution.
     *
     * @return the integer value of the counter
     */
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
     */
    void set(Int value);

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
        assert transactional && count >= 1;
        Int oldValue = get();
        Int newValue = oldValue + count;
        set(newValue);
        return oldValue;
        }

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
    @Op void increment()
        {
        adjustBy(1);
        }

    /**
     * Decrement the count. This method is a "blind update", which is designed to maximize
     * concurrency by being composable with other blind updates.
     */
    @Op void decrement()
        {
        adjustBy(-1);
        }

    /**
     * Increment the count and return the new incremented counter value. This method will negatively
     * impact concurrency for a transactional counter, because it both reads and writes the counter
     * value, which makes it non-composable.
     *
     * @return the counter value after the increment
     */
    @Op Int preIncrement()
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
    @Op Int preDecrement()
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
    @Op Int postIncrement()
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
    @Op Int postDecrement()
        {
        Int result = get();
        decrement();
        return result;
        }

    /**
     * Represents values emitted and/or operations conducted by a transactional database counter.
     */
    @Override
    interface Change
        {
        /**
         * True iff the counter was read during the transaction.
         */
        @RO Boolean valueRead;

        /**
         * True iff the counter was written during the transaction.
         */
        @RO Boolean valueModified;
        }
    }
