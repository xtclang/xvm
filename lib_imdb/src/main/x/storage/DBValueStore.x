/**
 * In-memory store for a DBValue.
 */
class DBValueStore<Value extends immutable Const>
        extends DBObjectStore(info, errs)
    {
    construct (DBObjectInfo info, Appender<String> errs, Value initial)
        {
        construct DBObjectStore(info, errs);

        value = initial;
        }

    // ----- master view ---------------------------------------------------------------------------

    /**
     * The value.
     */
    protected Value value;

    Value getValue()
        {
        return value;
        }

    void setValue(Value value)
        {
        this.value = value;
        }


    // ----- transactional -------------------------------------------------------------------------

    /**
     * Transactional changes keyed by the client id.
     */
    // protected Map<Int, TxChange> valueAt = new SkiplistMap<Int, TxChange>(); // TODO GG: not virtual!

    protected @Lazy Map<Int, TxChange> valueAt.calc()
        {
        return createStore();
        }

    // TODO GG: remove this
    protected Map<Int, TxChange> createStore()
        {
        return new SkiplistMap<Int, TxChange>();
        }

    Value getValueAt(Int clientId)
        {
        return valueAt.computeIfAbsent(clientId, () -> new TxChange(value)).get();
        }

    void setValueAt(Int clientId, Value newValue)
        {
        valueAt.computeIfAbsent(clientId, () -> new TxChange(value)).set(newValue);
        }

    @Override
    void apply(Int clientId)
        {
        if (TxChange change := valueAt.get(clientId))
            {
            setValue(change.newValue);
            valueAt.remove(clientId);
            }
        }

    @Override
    void discard(Int clientId)
        {
        valueAt.remove(clientId);
        }

    class TxChange
            implements oodb.DBValue<Value>.TxChange
        {
        construct(Value initial)
            {
            oldValue = initial;
            newValue = initial;
            }

        @Override Value oldValue;
        @Override Value newValue;

        @Override
        oodb.DBValue<Value> pre.get()
            {
            TODO("read-only DBValue");
            }

        @Override
        oodb.DBValue<Value> post.get()
            {
            TODO("read-only DBValue");
            }

        Value get()
            {
            return newValue;
            }

        void set(Value value)
            {
            newValue = value;
            }
        }
    }