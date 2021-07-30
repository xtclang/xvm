/**
 * In-memory store for a DBCounter.
 */
class DBCounterStore(DBObjectInfo info, Appender<String> errs)
        extends DBValueStore<Int>(info, errs, Int:0) // TODO GG
    {
    // ----- master view ---------------------------------------------------------------------------

    void adjustBy(Int value)
        {
        this.value += value;
        }


    // ----- transactional -------------------------------------------------------------------------

    /**
     * Transactional changes keyed by the client id.
     */
    void adjustByAt(Int clientId, Int value)
        {
        valueAt.computeIfAbsent(clientId, () -> new TxChange(0)).adjustBy(value);
        }

    @Override
    void apply(Int clientId)
        {
        if (TxChange change := valueAt.get(clientId))
            {
            if (change.relativeOnly)
                {
                adjustBy(change.newValue);
                }
            else
                {
                setValue(change.newValue);
                }
            valueAt.remove(clientId);
            }
        }

    @Override
    class TxChange
            implements oodb.DBCounter.TxChange
        {
        construct(Int value)
            {
            oldValue     = value;
            newValue     = value;
            relativeOnly = True;
            }

        /**
         * If the `relativeValue` is `True`, the `newValue` represents an adjustment and the
         * `oldValue` has no meaning.
         */
        @Override
        Boolean relativeOnly;

        @Override
        Int get()
            {
            if (relativeOnly)
                {
                oldValue     = this.DBCounterStore.getValue();
                newValue    += oldValue;
                relativeOnly = False;
                }
            return newValue;
            }

        @Override
        void set(Int value)
            {
            if (relativeOnly)
                {
                oldValue     = this.DBCounterStore.getValue();
                relativeOnly = False;
                }
            newValue = value;
            }

        void adjustBy(Int value)
            {
            newValue += value;
            }
        }
    }