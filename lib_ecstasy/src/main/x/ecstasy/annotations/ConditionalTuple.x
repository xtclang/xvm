/**
 * The ConditionalTuple annotation represents a tuple whose first field is a Boolean, and access to
 * any further fields, and any modification, is only permitted if the first field Boolean is True.
 */
annotation ConditionalTuple
        into Tuple<Boolean> {
    @Override
    @Op("[]") Object getElement(Int index) {
        assert index == 0 || super(0) == True;
        return super(index);
    }

    @Override
    @Op("+") <Element> Tuple!<> add(Element value) {
        assert this[0];
        return super(value);
    }

    @Override
    @Op("+") Tuple!<> addAll(Tuple!<> that) {
        assert this[0];
        return super(that);
    }

    @Override
    ConditionalTuple replace(Int index, Object value) {
        assert this[0] && index > 0;
        return super(index, value);
    }

    @Override
    @Op("[..]") Tuple!<> slice(Range<Int> indexes) {
        assert indexes.effectiveUpperBound == 0 || this[0] == True;
        return super(indexes);
    }

    @Override
    Tuple!<> remove(Int index) {
        assert this[0];
        return super(index);
    }

    @Override
    Tuple!<> removeAll(Interval<Int> interval) {
        assert this[0];
        return super(interval);
    }
}