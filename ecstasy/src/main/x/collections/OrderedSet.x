/**
 * An OrderedSet is an extension to the Set interface that exposes capabilities that are dependent
 * on an ordering of the elements in the Set.
 */
interface OrderedSet<Element extends Orderable>
        extends Set<Element>
        extends Sliceable<Element>
    {
    @Override
    conditional Orderer ordered();

    /**
     * Obtain the first element in the OrderedSet.
     *
     * @return the True iff the Set is not empty
     * @return (conditional) the first element in the OrderedSet
     */
    conditional Element first();

    /**
     * Obtain the last element in the OrderedSet.
     *
     * @return the True iff the Set is not empty
     * @return (conditional) the last element in the OrderedSet
     */
    conditional Element last();

    /**
     * Obtain the element that comes immediately after the specified element in the Set.
     *
     * @param element  a element that may _or may not be_ already present in the Set
     *
     * @return the True iff the Set is not empty and has a element that comes after the specified
     *         element
     * @return (conditional) the next element
     */
    conditional Element next(Element element);

    /**
     * Obtain the element that comes immediately before the specified element in the Set.
     *
     * @param element  a element that may _or may not be_ already present in the Set
     *
     * @return the True iff the Set is not empty and has a element that comes before the specified
     *         element
     * @return (conditional) the previous element
     */
    conditional Element prev(Element element);

    /**
     * Obtain the element that comes at or immediately after the specified element in the Set.
     *
     * @param element  a element that may _or may not be_ already present in the Set
     *
     * @return the True iff the Set is not empty and has a element that comes at or after the
     *         specified element
     * @return (conditional) the element that was passed in, if it exists in the Set, otherwise the
     *         [next] element
     */
    conditional Element ceiling(Element element)
        {
        if (contains(element))
            {
            return True, element;
            }

        return next(element);
        }

    /**
     * Obtain the element that comes at or immediately before the specified element in the Set.
     *
     * @param element  a element that may _or may not be_ already present in the Set
     *
     * @return the True iff the Set is not empty and has a element that comes at or before the
     *         specified element
     * @return (conditional) the element that was passed in, if it exists in the Set, otherwise the
     *         [prev] element
     */
    conditional Element floor(Element element)
        {
        if (contains(element))
            {
            return True, element;
            }

        return prev(element);
        }


    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two ordered sets are equal iff they are they contain the same elements in the same order.
     *
     * @param set1  the first ordered set
     * @param set2  the second ordered set
     *
     * @return True iff the sets are equal, according to the definition of an ordered set
     */
    static <CompileType extends OrderedSet> Boolean equals(CompileType set1, CompileType set2)
        {
        // some simple optimizations: two empty sets are equal, and two sets of different sizes are
        // not equal
        if (Int size1 := set1.knownSize(), Int size2 := set2.knownSize())
            {
            if (size1 != size2)
                {
                return False;
                }
            else if (size1 == 0)
                {
                return True;
                }
            }
        else
            {
            switch (set1.empty, set2.empty)
                {
                case (False, False):
                    break;

                case (False, True ):
                case (True , False):
                    return False;

                case (True , True ):
                    return True;
                }
            }

        // compare all of the elements in the two ordered sets, in the order that they appear
        using (Iterator<CompileType.Element> iter1 = set1.iterator(),
               Iterator<CompileType.Element> iter2 = set2.iterator())
            {
            while (CompileType.Element e1 := iter1.next())
                {
                if (CompileType.Element e2 := iter2.next())
                    {
                    if (e1 != e2)
                        {
                        return False;
                        }
                    }
                else
                    {
                    return False;
                    }
                }

            return !iter2.next();
            }
        }
    }
