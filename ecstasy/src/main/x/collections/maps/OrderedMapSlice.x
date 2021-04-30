/**
 * An implementation of the `OrderedMap` interface that represents a slice of another `OrderedMap`.
 */
class OrderedMapSlice<Key extends Orderable, Value>
        implements OrderedMap<Key, Value>
        incorporates KeySetBasedMap<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an OrderedMapSlice map, which provides view a slice (defined by a range of keys)
     * of an underlying map.
     *
     * @param map    the underlying map to create a slice from
     * @param range  the range of keys to include in the slice; the keys specified by the range do
     *               not have to actually exist in the underlying map
     */
    construct(OrderedMap<Key, Value> map, Range<Key> range)
        {
        this.map   = map;
        this.range = range;

        assert Orderer compare := map.ordered();
        Key first = range.first;
        Key last  = range.last;
        if (compare(first, last) == Greater)
            {
            // the slice is in the reverse order of the underlying map
            this.descending = True;
            this.compare    = (k1, k2) -> compare(k2, k1);
            this.include    = switch (range.lastExclusive, range.firstExclusive)
                {
                case (False, False): k -> compare(k, last) != Lesser  && compare(k, first) != Greater;
                case (False, True ): k -> compare(k, last) != Lesser  && compare(k, first) == Lesser ;
                case (True,  False): k -> compare(k, last) == Greater && compare(k, first) != Greater;
                case (True,  True ): k -> compare(k, last) == Greater && compare(k, first) == Lesser ;
                };
            }
        else
            {
            this.descending = False;
            this.compare    = compare;
            this.include    = switch (range.firstExclusive, range.lastExclusive)
                {
                case (False, False): k -> compare(k, first) != Lesser  && compare(k, last) != Greater;
                case (False, True ): k -> compare(k, first) != Lesser  && compare(k, last) == Lesser ;
                case (True,  False): k -> compare(k, first) == Greater && compare(k, last) != Greater;
                case (True,  True ): k -> compare(k, first) == Greater && compare(k, last) == Lesser ;
                };
            }

        findFirst = switch (descending, range.firstExclusive)
            {
            case (False, False): map.&ceiling(range.first);
            case (False, True ): map.&next(range.first);
            case (True , False): map.&floor(range.first);
            case (True , True ): map.&prev(range.first);
            };

        findLast = switch (descending, range.lastExclusive)
            {
            case (False, False): map.&floor(range.last);
            case (False, True ): map.&prev(range.last);
            case (True , False): map.&ceiling(range.last);
            case (True , True ): map.&next(range.last);
            };

        (findNext, findPrev) = descending
                ? (map.prev, map.next)
                : (map.next, map.prev);
        }
    finally
        {
        if (&map.isImmutable || &map.isService)
            {
            makeImmutable();
            }
        }


    // ----- internal state ------------------------------------------------------------------------

    /**
     * The underlying map.
     */
    protected/private OrderedMap<Key, Value> map;

    /**
     * The actual Orderer used internally.
     */
    protected/private Orderer compare;

    /**
     * The key range that defines the slice. Note that the order of the range is always based on
     * the natural order of the `Key` type, and so the `Range` logic has to be overridden
     * (re-implemented here) to use the map's specified `Orderer`.
     */
    protected/private Range<Key> range;

    /**
     * A custom test function for key inclusion that is configured during construction of the slice.
     */
    protected/private function Boolean(Key) include;

    /**
     * True iff the slice appears to be in the reversed order of the original map.
     */
    protected/private Boolean descending;

    /**
     * The function that provides the first key in this slice, although the key still must be tested
     * for inclusion.
     */
    function conditional Key() findFirst;

    /**
     * The function that provides the last key in this slice, although the key still must be tested
     * for inclusion.
     */
    function conditional Key() findLast;

    /**
     * The function that provides the next key in this slice, although the key still must be tested
     * for inclusion.
     */
    function conditional Key(Key) findNext;

    /**
     * The function that provides the previous key in this slice, although the key still must be
     * tested for inclusion.
     */
    function conditional Key(Key) findPrev;


    // ----- Map interface -------------------------------------------------------------------------

    @Override
    Int size.get()
        {
        if (Key first := this.first(), Key last := this.last())
            {
            if (&first == &last)
                {
                return 1;
                }

            Int count = 2; // first & last

            // the assumption is that it's faster to advance through the underlying map in the
            // order of its entries
            (Key fromKey, Key toKey, function conditional Key(Key) advance) = descending
                    ? (last, first, findPrev)
                    : (first, last, findNext);

            while (True)
                {
                assert fromKey := advance(fromKey);
                if (&fromKey == &toKey)
                    {
                    return count;
                    }

                ++count;
                }
            }

        return 0;
        }

    @Override
    @RO Boolean empty.get()
        {
        // the assumption is that it's faster to find the first than the last in the underlying map
        return (descending ? !last() : !first());
        }

    @Override
    @Lazy public/private Set<Key> keys.calc()
        {
        return new KeySet();
        }

    @Override
    Boolean contains(Key key)
        {
        return map.contains(key) && include(key);
        }

    @Override
    conditional Value get(Key key)
        {
        TODO include map.get
        }

    @Override
    OrderedMapSlice put(Key key, Value value)
        {
        TODO
        return this;
        }

    @Override
    OrderedMapSlice remove(Key key)
        {
        TODO
        return this;
        }

    @Override
    OrderedMapSlice clear()
        {
        TODO
        return this;
        }


    // ----- OrderedMap interface ------------------------------------------------------------------

    @Override
    conditional Orderer ordered()
        {
        return True, compare;
        }

    @Override
    conditional Key first()
        {
        if (Key key := findFirst(), include(key))
            {
            return True, key;
            }
        return False;
        }

    @Override
    conditional Key last()
        {
        if (Key key := findLast(), include(key))
            {
            return True, key;
            }
        return False;
        }

    @Override
    conditional Key next(Key key)
        {
        if (key := findNext(key), include(key))
            {
            return True, key;
            }
        return False;
        }

    @Override
    conditional Key prev(Key key)
        {
        if (key := findPrev(key), include(key))
            {
            return True, key;
            }
        return False;
        }

    @Override
    conditional Key ceiling(Key key)
        {
        if (key := descending ? map.floor(key) : map.ceiling(key), include(key))
            {
            return True, key;
            }

        return False;
        }

    @Override
    conditional Key floor(Key key)
        {
        if (key := descending ? map.ceiling(key) : map.floor(key), include(key))
            {
            return True, key;
            }

        return False;
        }


    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") OrderedMap<Key, Value> slice(Range<Key> indexes)
        {
        // use the orderer instead of the range's own logic, since the custom key ordering
        // doesn't necessarily match the natural ordering used by the range
        Key lower1;
        Key upper1;
        Boolean excludeLower1;
        Boolean excludeUpper1;
        if (compare(range.first, range.last) == Greater)
            {
            lower1         = range.last;
            excludeLower1  = range.lastExclusive;
            upper1         = range.first;
            excludeUpper1  = range.firstExclusive;
            }
        else
            {
            lower1         = range.first;
            excludeLower1  = range.firstExclusive;
            upper1         = range.last;
            excludeUpper1  = range.lastExclusive;
            }

        Key lower2;
        Key upper2;
        Boolean excludeLower2;
        Boolean excludeUpper2;
        Boolean reverse;
        if (compare(range.first, range.last) == Greater)
            {
            reverse        = True;
            lower2         = range.last;
            excludeLower2  = range.lastExclusive;
            upper2         = range.first;
            excludeUpper2  = range.firstExclusive;
            }
        else
            {
            reverse        = False;
            lower2         = range.first;
            excludeLower2  = range.firstExclusive;
            upper2         = range.last;
            excludeUpper2  = range.lastExclusive;
            }

        Key lower;
        Boolean excludeLower;
        switch (compare(lower1, lower2))
            {
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

        Key upper;
        Boolean excludeUpper;
        switch (compare(upper1, upper2))
            {
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

        return new OrderedMapSlice(map, indexes);
        }

    @Override
    @Op("[[..]]") OrderedMap<Key, Value> sliceInclusive(Range<Index> indexes)
        {
        return slice(indexes.ensureInclusive());
        }

    @Override
    @Op("[[..)]") OrderedMap<Key, Value> sliceExclusive(Range<Index> indexes)
        {
        return slice(indexes.ensureExclusive());
        }

    @Override
    OrderedMap<Key, Value> reify()
        {
        return new SkiplistMap<Key, Value>(size, compare).putAll(this);
        }


    // ----- KeySet implementation -----------------------------------------------------------------

    /**
     * A representation of all of the Keys in the Map.
     */
    protected class KeySet
            implements Set<Key>
        {
        @Override
        Int size.get()
            {
            return outer.size;
            }

        @Override
        Boolean empty.get()
            {
            return outer.empty;
            }

        @Override
        Iterator<Element> iterator()
            {
            return new KeyIterator();
            }

        /**
         * An iterator implementation with the following guarantees:
         *
         * * Resilient to changes in the original map, including additions and removals;
         * * Iterates in the original map's order;
         * * Regardless of the order of changes, does not ever emit the same element twice;
         * * Regardless of the order of changes, does not ever emit an element that is no longer
         *   present in the underlying map;
         * * For elements added to the original map, those that occur in the order before the
         *   most recently emitted element will never be emitted, and those that occur in the order
         *   after the most recently emitted element _will_ be emitted.
         */
        protected class KeyIterator
                implements Iterator<Key>
            {
            // ----- properties ---------------------------------------------------------------

            /**
             * Set to true once iteration has begun.
             */
            protected/private Boolean started;

            /**
             * Once iteration has started, this is the previously iterated key.
             */
            protected/private Key? prevKey = Null;

            /**
             * Set to true once the iterator has been exhausted.
             */
            protected/private Boolean finished.set(Boolean done)
                {
                // make sure that the iterator has been marked as having started if it is finished
                if (done)
                    {
                    started = True;
                    }

                super(done);
                }


            // ----- Iterator interface -------------------------------------------------------

            @Override
            conditional Element next()
                {
                if (finished)
                    {
                    return False;
                    }

                if (started)
                    {
                    if (Key key := this.OrderedMapSlice.next(prevKey.as(Key)))
                        {
                        prevKey = key;
                        return True, key;
                        }
                    else
                        {
                        finished = True;
                        return False;
                        }
                    }

                if (Key key := this.OrderedMapSlice.first())
                    {
                    started = True;
                    prevKey = key;
                    return True, key;
                    }
                else
                    {
                    finished = True;
                    return False;
                    }
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                if (finished)
                    {
                    return True, 0;
                    }

                return False;
                }

            @Override
            (KeyIterator, KeyIterator) bifurcate()
                {
                return finished
                        ? (this, this)
                        : (this, clone());
                }


            // ----- internal -----------------------------------------------------------------

            /**
             * Copy constructor.
             */
            protected KeyIterator clone()
                {
                KeyIterator that = new KeyIterator();

                that.started  = this.started;
                that.prevKey  = this.prevKey;
                that.finished = this.finished;

                return that;
                }
            }

        @Override
        conditional Orderer? ordered()
            {
            return True, outer.compare;
            }

        @Override
        Boolean contains(Key key)
            {
            return outer.contains(key);
            }

        @Override
        KeySet remove(Key key)
            {
            outer.remove(key);
            return this;
            }
        }
    }
