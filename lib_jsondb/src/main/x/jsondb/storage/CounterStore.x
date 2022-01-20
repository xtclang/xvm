/**
 * Storage API for a database counter.
 */
interface CounterStore
        extends ValueStore<Int>
    {
    @Override
    Int load(Int txId);

    @Override
    void store(Int txId, Int value);

    /**
     * Modify the counter value _in a relative manner_ by applying the passed delta value to the
     * current value, returning both the value before and after the change.
     *
     * @param txId   the transaction identifier
     * @param delta  the relative value adjustment to make to the counter
     *
     * @return before  the value before the change
     * @return after   the value after the change
     */
    (Int before, Int after) adjust(Int txId, Int delta = 1)
        {
        Int before = load(txId);
        Int after  = before + delta;
        store(txId, after);
        return before, after;
        }

    /**
     * Modify the counter value _in a relative manner_ by applying the passed delta value to the
     * current value, but not accessing either the "before" or "after" value.
     *
     * @param txId   the transaction identifier
     * @param delta  the relative value adjustment to make to the counter
     */
    void adjustBlind(Int txId, Int delta = 1)
        {
        // this can be optimized in transactional implementations
        adjust(txId, delta);
        }
    }
