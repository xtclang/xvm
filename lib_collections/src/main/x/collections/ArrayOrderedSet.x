import ecstasy.collections.OrderedSetSlice;

import ecstasy.Duplicable;


/**
 * An ArrayOrderedSet allows a sorted array of data to be used as a set with O(log n) performance
 * (i.e. using binary search).
 *
 * TODO delegate most non-mutating methods straight to the array
 */
class ArrayOrderedSet<Element>
        implements OrderedSet<Element>
        implements Duplicable
        implements Freezable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Create an ArrayOrderedSet from an array of values.
     */
    construct(Element[] elements, Orderer? compare=Null)
        {
        // if you cannot afford a compare function, one will be provided for you
        compare ?:= (v1, v2) -> v1 <=> v2;

        assert:test
            {
            if (elements.size <= 1)
                {
                return True;
                }

            val iter = elements.iterator();
            assert Element prev := iter.next();
            while (Element value := iter.next())
                {
                if (compare(prev, value) != Lesser)
                    {
                    return False;
                    }
                prev = value;
                }
            return True;
            } as $"elements are not provided in order: {elements}";

        this.array   = elements;
        this.compare = compare;
        }

    @Override
    construct(ArrayOrderedSet that)
        {
        // this.array   = that.array.duplicate(); // TODO CP
        this.array   = that.array.clone();
        this.compare = that.compare;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying array that holds the contents of the set.
     */
    protected/private Element[] array;

    /**
     * The Orderer for the set.
     */
    protected/private Orderer compare;


    // ----- read operations -----------------------------------------------------------------------

    @Override
    Int size.get()
        {
        return array.size;
        }

    @Override
    Iterator<Element> iterator()
        {
        return array.iterator();
        }

    @Override
    conditional Orderer ordered()
        {
        return True, compare;
        }

    @Override
    Boolean contains(Element value)
        {
        return find(value);
        }

    @Override
    conditional Element first()
        {
        return array.first();
        }

    @Override
    conditional Element last()
        {
        return array.last();
        }

    @Override
    conditional Element next(Element value)
        {
        (Boolean found, Int index) = find(value);
        if (found)
            {
            ++index;
            }

        if (index >= array.size)
            {
            return False;
            }

        return True, array[index];
        }

    @Override
    conditional Element prev(Element value)
        {
        (_, Int index) = find(value);

        if (--index < 0)
            {
            return False;
            }

        return True, array[index];
        }

    @Override
    conditional Element ceiling(Element value)
        {
        (Boolean found, Int index) = find(value);
        if (found)
            {
            return True, value;
            }

        if (index >= array.size)
            {
            return False;
            }

        return True, array[index];
        }

    @Override
    conditional Element floor(Element value)
        {
        (Boolean found, Int index) = find(value);
        if (found)
            {
            return True, value;
            }

        if (index == 0)
            {
            return False;
            }

        return True, array[--index];
        }

    @Override
    @Op("[..]") OrderedSet<Element> slice(Range<Element> indexes)
        {
        return new OrderedSetSlice<Element>(this, indexes);
        }


    // ----- Freezable interface -------------------------------------------------------------------

    @Override
    immutable ArrayOrderedSet freeze(Boolean inPlace = False)
        {
        if (this.is(immutable ArrayOrderedSet))
            {
            return this;
            }

        if (inPlace)
            {
            array = array.freeze(True);
            return makeImmutable();
            }

        return new ArrayOrderedSet(this).freeze(True);
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Binary search the underlying array.
     *
     * @param value  the element value to search for
     *
     * @return True if the element value was found
     * @return the index of the value, if it was found, otherwise the insertion point for the value
     */
    (Boolean found, Int index) find(Element value)
        {
        Element[] array = this.array;

        Int lo = 0;
        Int hi = array.size - 1;
        while (lo <= hi)
            {
            Int mid = (lo + hi) >> 1;
            switch (compare(value, array[mid]))
                {
                case Lesser:
                    // go left
                    hi = mid - 1;
                    break;

                case Equal:
                    return True, mid;

                case Greater:
                    // go right
                    lo = mid + 1;
                    break;
                }
            }

        return False, lo;
        }
    }