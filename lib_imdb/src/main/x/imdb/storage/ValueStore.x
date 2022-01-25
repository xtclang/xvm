/**
 * In-memory store for a DBValue.
 */
class ValueStore<Value extends immutable Const>
        extends ObjectStore(info, errs)
    {
    construct (DBObjectInfo info, Appender<String> errs, Value initial)
        {
        super(info, errs);

        value   = initial;
        valueAt = new SkiplistMap();
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
    protected Map<Int, TxChange> valueAt;

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