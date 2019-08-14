import collections.Stream;
import collections.VariablyMutable;

/**
 * The Iterable interface allows an object to expose its contents as a series of elements, either
 * for consumption by the caller or by inversion of control by passing an element-consuming
 * function.
 */
interface Iterable<ElementType>
    {
    /**
     * Determine the size of the Iterable object, which is the number of elements that an iterator
     * would emit.
     */
    @RO Int size;

    /**
     * Obtain an iterator over the contents of the Iterable object.
     *
     * @return an Iterator
     */
    Iterator<ElementType> iterator();

    /**
     * Obtain an iterator over a portion of the contents of the Iterable object.
     *
     * @param match  a function that filters which items should be exposed through the iterator
     *
     * @return an iterator that produces elements that match the specified predicate
     */
    Iterator<ElementType> iterator(function Boolean (ElementType) match)
        {
        return new Iterator<ElementType>()
            {
            Iterator<ElementType> iter = iterator();

            @Override
            conditional ElementType next()
                {
                while (ElementType value := iter.next())
                    {
                    if (match(value))
                        {
                        return true, value;
                        }
                    }

                return false;
                }
            };
        }

    /**
     * Determine if the iterable source would emit the specified value via an iterator.
     *
     * @param value  the value to search for in this iterable source
     *
     * @return {@code True} iff the specified value exists in this iterable source
     */
    Boolean contains(ElementType value)
        {
        // this should be overridden by any implementation that has a structure that can do better
        // than an O(n) search, such as a sorted structure (binary search) or a hashed structure
        return iterator().untilAny(element -> element == value);
        }

    /**
     * @return a Stream over the contents of this iterable source
     */
    Stream<ElementType> stream()
        {
        TODO return new SimpleStream(this);
        }

    /**
     * Obtain the contents of this iterable source as an array.
     *
     * @param mutability  the requested Mutability of the resulting array
     *
     * @return an array of elements from this iterable source
     */
    ElementType[] toArray(VariablyMutable.Mutability mutability = Persistent)
        {
        ElementType[] result = new Array<ElementType>(size); // mutable

        loop: for (ElementType element : this)
            {
            result[loop.count] = element;
            }

        return switch (mutability)
            {
            case Mutable   : result;
            case Fixed     : result.ensureFixedSize (True);
            case Persistent: result.ensurePersistent(True);
            case Constant  : result.ensureConst     (True);
            };
        }
    }
