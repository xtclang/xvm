/**
 * The OrderedSetSlice is an implementation of the [OrderedSet] interface that represents a slice
 * of an underlying `OrderedSet` instance as its storage.
 */
class OrderedSetSlice<Element extends Orderable>
        implements OrderedSet<Element> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an OrderedSetSlice set, which provides view a slice (defined by a range of
     * elements) of an underlying set.
     *
     * @param set    the underlying set to create a slice from
     * @param range  the range of elements to include in the slice; the elements specified by the
     *               range do not have to actually exist in the underlying set
     */
    construct(OrderedSet<Element> set, Range<Element> range) {
        // while this implementation could be altered to support persistent set implementations, it
        // currently assumes that the underlying set will mutate in-place
        assert set.inPlace;

        this.set   = set;
        this.range = range;

        assert Orderer compare := set.ordered();
        Element first = range.first;
        Element last  = range.last;
        if (compare(first, last) == Greater) {
            // the slice is in the reverse order of the underlying set
            this.descending = True;
            this.compare    = (v1, v2) -> compare(v2, v1);
            this.include    = switch (range.lastExclusive, range.firstExclusive) {
                case (False, False): v -> compare(v, last) != Lesser  && compare(v, first) != Greater;
                case (False, True ): v -> compare(v, last) != Lesser  && compare(v, first) == Lesser ;
                case (True,  False): v -> compare(v, last) == Greater && compare(v, first) != Greater;
                case (True,  True ): v -> compare(v, last) == Greater && compare(v, first) == Lesser ;
            };
        } else {
            this.descending = False;
            this.compare    = compare;
            this.include    = switch (range.firstExclusive, range.lastExclusive) {
                case (False, False): v -> compare(v, first) != Lesser  && compare(v, last) != Greater;
                case (False, True ): v -> compare(v, first) != Lesser  && compare(v, last) == Lesser ;
                case (True,  False): v -> compare(v, first) == Greater && compare(v, last) != Greater;
                case (True,  True ): v -> compare(v, first) == Greater && compare(v, last) == Lesser ;
            };
        }

        findFirst = switch (descending, range.firstExclusive) {
            case (False, False): set.&ceiling(range.first);
            case (False, True ): set.&next(range.first);
            case (True , False): set.&floor(range.first);
            case (True , True ): set.&prev(range.first);
        };

        findLast = switch (descending, range.lastExclusive) {
            case (False, False): set.&floor(range.last);
            case (False, True ): set.&prev(range.last);
            case (True , False): set.&ceiling(range.last);
            case (True , True ): set.&next(range.last);
        };

        (findNext, findPrev) = descending
                ? (set.prev, set.next)
                : (set.next, set.prev);
    } finally {
        if (&set.isImmutable || &set.isService) {
            makeImmutable();
        }
    }

    // ----- internal state ------------------------------------------------------------------------

    /**
     * The underlying set.
     */
    protected/private OrderedSet<Element> set;

    /**
     * The actual Orderer used internally.
     */
    protected/private Orderer compare;

    /**
     * The element range that defines the slice. Note that the order of the range is always based on
     * the natural order of the `Element` type, and so the `Range` logic has to be overridden
     * (re-implemented here) to use the set's specified `Orderer`.
     */
    protected/private Range<Element> range;

    /**
     * A custom test function for element inclusion that is configured during construction of the
     * slice.
     */
    protected/private function Boolean(Element) include;

    /**
     * `True` iff the slice appears to be in the reversed order of the original set.
     */
    protected/private Boolean descending;

    /**
     * The function that provides the first element in this slice, although the element still must
     * be tested for inclusion.
     */
    function conditional Element() findFirst;

    /**
     * The function that provides the last element in this slice, although the element still must be
     * tested for inclusion.
     */
    function conditional Element() findLast;

    /**
     * The function that provides the next element in this slice, although the element still must be
     * tested for inclusion.
     */
    function conditional Element(Element) findNext;

    /**
     * The function that provides the previous element in this slice, although the element still
     * must be tested for inclusion.
     */
    function conditional Element(Element) findPrev;


    // ----- Set interface -------------------------------------------------------------------------

    @Override
    conditional Int knownSize() = empty ? (True, 0) : False;

    @Override
    @RO Boolean empty.get() {
        // the assumption is that it's faster to find the first than the last in the underlying set
        return set.empty || (descending ? !last() : !first());
    }

    @Override
    Int size.get() {
        if (Element first := this.first(), Element last := this.last()) {
            if (&first == &last) {
                return 1;
            }

            Int count = 2; // first & last

            // the assumption is that it's faster to advance through the underlying set in the
            // order of its entries
            (Element from, Element to, function conditional Element(Element) advance) = descending
                    ? (last, first, findPrev)
                    : (first, last, findNext);

            while (True) {
                assert from := advance(from);
                if (&from == &to) {
                    return count;
                }

                ++count;
            }
        }

        return 0;
    }

    @Override
    Iterator<Element> iterator() = new ElementIterator();

    /**
     * An [Iterator] implementation with the following guarantees:
     *
     * * Resilient to changes in the original `OrderedSet`, including additions and removals;
     * * Iterates in the original `OrderedSet`'s order;
     * * Regardless of the order of changes, does not ever emit the same element twice;
     * * Regardless of the order of changes, does not ever emit an element that is no longer
     *   present in the underlying `OrderedSet`;
     * * For elements added to the original `OrderedSet`, those that occur in the order before the
     *   most recently emitted element will never be emitted, and those that occur in the order
     *   after the most recently emitted element _will_ be emitted.
     */
    protected class ElementIterator
            implements Iterator<Element> {
        // ----- properties ---------------------------------------------------------------

        /**
         * Set to `True` once iteration has begun.
         */
        protected/private Boolean started;

        /**
         * Once iteration has started, this is the previously iterated value.
         */
        protected/private Element? previous = Null;

        /**
         * Set to `True` once the iterator has been exhausted.
         */
        protected/private Boolean finished.set(Boolean done) {
            // make sure that the iterator has been marked as having started if it is finished
            if (done) {
                started = True;
            }

            super(done);
        }

        // ----- Iterator interface -------------------------------------------------------

        @Override
        conditional Element next() {
            if (finished) {
                return False;
            }

            if (started) {
                if (Element value := this.OrderedSetSlice.next(previous.as(Element))) {
                    previous = value;
                    return True, value;
                } else {
                    finished = True;
                    return False;
                }
            }

            if (Element value := this.OrderedSetSlice.first()) {
                started  = True;
                previous = value;
                return True, value;
            } else {
                finished = True;
                return False;
            }
        }

        @Override
        Boolean knownDistinct() = True;

        @Override
        conditional Int knownSize() {
            if (finished) {
                return True, 0;
            }

            return False;
        }

        @Override
        (ElementIterator, ElementIterator) bifurcate() {
            return finished
                    ? (this, this)
                    : (this, clone());
        }

        // ----- internal -----------------------------------------------------------------

        /**
         * Copy constructor.
         */
        protected ElementIterator clone() {
            ElementIterator that = new ElementIterator();

            that.started  = this.started;
            that.previous = this.previous;
            that.finished = this.finished;

            return that;
        }
    }

    @Override
    Boolean contains(Element value) = set.contains(value) && include(value);

    @Override
    @Op("+") OrderedSetSlice add(Element value) {
        assert:bounds include(value);
        set.add(value);
        return this;
    }

    @Override
    @Op("-") OrderedSetSlice remove(Element value) {
        if (include(value)) {
            set.remove(value);
        }

        return this;
    }

    @Override
    OrderedSetSlice clear() {
        while (Element value := first()) {
            set.remove(value);
        }
        return this;
    }

    // ----- OrderedSet interface ------------------------------------------------------------------

    @Override
    conditional Orderer ordered() = (True, compare);

    @Override
    conditional Element first() {
        if (Element value := findFirst(), include(value)) {
            return True, value;
        }
        return False;
    }

    @Override
    conditional Element last() {
        if (Element value := findLast(), include(value)) {
            return True, value;
        }
        return False;
    }

    @Override
    conditional Element next(Element value) {
        if (value := findNext(value), include(value)) {
            return True, value;
        }
        return False;
    }

    @Override
    conditional Element prev(Element value) {
        if (value := findPrev(value), include(value)) {
            return True, value;
        }
        return False;
    }

    @Override
    conditional Element ceiling(Element value) {
        if (value := descending ? set.floor(value) : set.ceiling(value), include(value)) {
            return True, value;
        }

        return False;
    }

    @Override
    conditional Element floor(Element value) {
        if (value := descending ? set.ceiling(value) : set.floor(value), include(value)) {
            return True, value;
        }

        return False;
    }

    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") OrderedSetSlice slice(Range<Element> indexes) {
        // use the orderer instead of the range's own logic, since the custom value ordering
        // doesn't necessarily match the natural ordering used by the range
        Element lower1;
        Element upper1;
        Boolean excludeLower1;
        Boolean excludeUpper1;
        if (compare(range.first, range.last) == Greater) {
            lower1         = range.last;
            excludeLower1  = range.lastExclusive;
            upper1         = range.first;
            excludeUpper1  = range.firstExclusive;
        } else {
            lower1         = range.first;
            excludeLower1  = range.firstExclusive;
            upper1         = range.last;
            excludeUpper1  = range.lastExclusive;
        }

        Element lower2;
        Element upper2;
        Boolean excludeLower2;
        Boolean excludeUpper2;
        Boolean reverse;
        if (compare(range.first, range.last) == Greater) {
            reverse        = True;
            lower2         = range.last;
            excludeLower2  = range.lastExclusive;
            upper2         = range.first;
            excludeUpper2  = range.firstExclusive;
        } else {
            reverse        = False;
            lower2         = range.first;
            excludeLower2  = range.firstExclusive;
            upper2         = range.last;
            excludeUpper2  = range.lastExclusive;
        }

        Element lower;
        Boolean excludeLower;
        switch (compare(lower1, lower2)) {
        case Lesser:
            lower        = lower2;
            excludeLower = excludeLower2;
            break;

        case Equal:
            lower        = lower1;
            excludeLower = excludeLower1 | excludeLower2;
            break;

        case Greater:
            lower        = lower1;
            excludeLower = excludeLower1;
            break;
        }

        Element upper;
        Boolean excludeUpper;
        switch (compare(upper1, upper2)) {
        case Lesser:
            upper        = upper1;
            excludeUpper = excludeUpper1;
            break;

        case Equal:
            upper        = upper1;
            excludeUpper = excludeUpper1 | excludeUpper2;
            break;

        case Greater:
            upper        = upper2;
            excludeUpper = excludeUpper2;
            break;
        }

        indexes = reverse
                ? upper..lower
                : lower..upper;

        return new OrderedSetSlice(set, indexes);
    }

    @Override
    OrderedSet<Element> reify() = new SkiplistSet<Element>(size, compare).addAll(this);
}
