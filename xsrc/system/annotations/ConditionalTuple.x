/**
 * The ConditionalTuple mixin represents a tuple whose first field is a Boolean, and access to any
 * further fields, and any modification, is only permitted if the first field Boolean is True.
 */
mixin ConditionalTuple
        into Tuple<Boolean>
    {
    @Override
    @Op Object getElement(Int index)
        {
        assert index == 0 || super(0) == true;
        return super(index);
        }

    @Override
    @Op void setElement(Int index, Object newValue)
        {
        assert this[0];
        super(index, newValue);
        }

    @Override
    @Op Tuple add(Tuple that)
        {
        assert this[0];
        return super(that);
        }

    @Override
    Tuple<ElementTypes> replace(Int index, Object value)
        {
        assert this[0] && index > 0;
        return super(index, value);
        }

    @Override
    @Op Tuple slice(Range<Int> range)
        {
        assert range.upperBound == 0 || this[0] == true;
        return super(range);
        }

    @Override
    Tuple remove(Int index)
        {
        assert this[0];
        return super(index);
        }

    @Override
    Tuple remove(Range<Int> range)
        {
        assert this[0];
        return super(range);
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
    immutable Tuple<ElementTypes> ensureConst(Boolean inPlace = false)
        {
        assert this[0];
        return super(inPlace);
        }
    }
