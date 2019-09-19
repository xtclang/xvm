import collections.VariablyMutable;

/**
 * The Iterable interface allows an object to expose its contents as a series of elements, either
 * for consumption by the caller or by inversion of control by passing an element-consuming
 * function.
 */
interface Iterable<Element>
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
    Iterator<Element> iterator();

    /**
     * Obtain an iterator over a portion of the contents of the Iterable object.
     *
     * @param match  a function that filters which items should be exposed through the iterator
     *
     * @return an iterator that produces elements that match the specified predicate
     */
    Iterator<Element> iterator(function Boolean (Element) match)
        {
        return new Iterator<Element>()
            {
            Iterator<Element> iter = iterator();

            @Override
            conditional Element next()
                {
                while (Element value := iter.next())
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
    Boolean contains(Element value)
        {
        // this should be overridden by any implementation that has a structure that can do better
        // than an O(n) search, such as a sorted structure (binary search) or a hashed structure
        return iterator().untilAny(element -> element == value);
        }

    /**
     * Obtain the contents of this iterable source as an array.
     *
     * @param mutability  the requested Mutability of the resulting array
     *
     * @return an array of elements from this iterable source
     */
    Element[] toArray(VariablyMutable.Mutability mutability = Persistent)
        {
        Element[] result = new Array<Element>(size); // mutable

        loop: for (Element element : this)
            {
            result[loop.count] = element;
            }

        return switch (mutability)
            {
            case Mutable   : result;
            case Fixed     : result.ensureFixedSize (True);
            case Persistent: result.ensurePersistent(True);
            case Constant  : result.ensureImmutable (True);
            };
        }
    }
