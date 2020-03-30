/**
 * The ConditionalTuple mixin represents a tuple whose first field is a Boolean, and access to any
 * further fields, and any modification, is only permitted if the first field Boolean is True.
 */
mixin ConditionalTuple
        into Tuple<Boolean>
    {
    @Override
    @Op("[]") Object getElement(Int index)
        {
        assert index == 0 || super(0) == true;
        return super(index);
        }

    @Override
    @Op("[]=") void setElement(Int index, Object newValue)
        {
        assert this[0];
        super(index, newValue);
        }

    @Override
    @Op("+") Tuple add(Tuple!<> that)
        {
        assert this[0];
        return super(that);
        }

    @Override
    Tuple replace(Int index, Object value)
        {
        assert this[0] && index > 0;
        return super(index, value);
        }

    @Override
    @Op("[..]") Tuple slice(Interval<Int> interval)
        {
        assert interval.effectiveUpperBound == 0 || this[0] == true;
        return super(interval);
        }

    @Override
    Tuple remove(Int index)
        {
        assert this[0];
        return super(index);
        }

    @Override
    Tuple remove(Interval<Int> interval)
        {
        assert this[0];
        return super(interval);
        }

    @Override
    Tuple<ElementTypes> ensureFixedSize(Boolean inPlace = false)
        {
        assert this[0];
        return super(inPlace);
        }

    @Override
    Tuple<ElementTypes> ensurePersistent(Boolean inPlace = false)
        {
        assert this[0];
        return super(inPlace);
        }

    @Override
    immutable Tuple<ElementTypes> ensureImmutable(Boolean inPlace = false)
        {
        assert this[0];
        return super(inPlace);
        }
    }
