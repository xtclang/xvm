/**
 * The [ConditionalTuple] annotation represents a [Tuple] whose first element is a [Boolean], and
 * access to any further elements and the ability to perform any modifications is subject to the
 * first element being the [Boolean] value `True`.
 */
annotation ConditionalTuple
        into Tuple<Boolean> {
    /**
     * The number of elements in the [ConditionalTuple], which is always `1` when the first element
     * contains a [Boolean] `False` value.
     */
    @Override
    @RO Int size.get() = this[0] ? super() : 1;

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