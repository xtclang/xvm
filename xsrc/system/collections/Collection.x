/**
 * A Collection is a container data structure that represents a group of _values_. There are a
 * number of generic capabilities and loose contracts defined by the Collection interface itself,
 * and specialization (with additional contractual constraints and clarifications) in several
 * derived interfaces, including:
 * * The Set interface defines a container for distinct values;
 * * The List interface defines a sequence of values.
 *
 * Collections are very common data structures. As with many data structures, it is expected that
 * implementations will support one or more of these four modes, whose general behaviors are
 * defined as:
 * * A *mutable* collection is one that allows items to be added and removed, and whose contents are
 *   generally not required to be immutable. If an implementation provides support for more than one
 *   mode, including a *mutable* mode, then it should implement the {@link MutableAble} interface.
 * * A *fixed size* collection is one that does not allow items to be added or removed, but whose
 *   values can be replaced, and whose contents are generally not required to be immutable.
 *   Requesting a persistent collection to add or remove contents will result in a new fixed
 *   size collection as a result of the request. If an implementation provides support for more than
 *   one mode, including a *fixed size* mode, then it should implement the {@link FixedSizeAble}
 *   interface.
 * * A *persistent* collection is one that does not allow items to be added or removed, whose values
 *   can not be replaced, but whose contents are generally not required to be immutable. Requesting
 *   a persistent collection to add, remove, or modify its contents will result in a new persistent
 *   collection as a result of the request. If an implementation provides support for more than one
 *   mode, including a *persistent* mode, then it should implement the {@link PersistentAble}
 *   interface.
 * * A *const* collection is one that is immutable, whose size and contents are immutable, and which
 *   provides a new *const* collection as the result of any mutating request. If an implementation
 *   provides support for more than one mode, including a *const* mode, then it should implement the
 *   {@link ImmutableAble} interface.
 */
interface Collection<Element>
        extends Iterable<Element>
        extends VariablyMutable
    {
    // ----- read operations -----------------------------------------------------------------------

    /**
     * Metadata: Is the collection limited to a distinct set of values?
     */
    @RO Boolean distinct.get()
        {
        return False;
        }

    /**
     * Metadata: Is the collection maintained in an order that is a function of the elements in the
     * collection? And if so, what is the Comparator that represents that ordering?
     */
    conditional Comparator<Element> sortedBy()
        {
        return False;
        }

    /**
     * Determine if the Collection is empty.
     *
     * This is equivalent to the following code, but may be implemented more efficiently for
     * Collection implementations that have a cost associated with calculating the size:
     *
     *   return size > 0;
     */
    @RO Boolean empty.get()
        {
        return size > 0;
        }

    /**
     * Determine if the collection contains all of the specified values.
     *
     * @param values  another collection containing values to search for in this collection
     *
     * @return `True` iff the specified values all exist in this collection
     */
    Boolean containsAll(Iterable<Element> values)
        {
        return values.iterator().whileEach(contains(_));
        }

    /**
     * Obtain a shallow clone of the Collection. The definition of "shallow clone" is that the new
     * collection will contain the same element references, but changes made to this collection
     * through the Collection interface will not appear in the returned collection, nor will changes
     * made to the returned collection through the Collection interface appear in this collection.
     *
     * @return a Collection such that changes to this Collection do not appear in that collection,
     *         nor vice versa
     */
    Collection clone();


    // ----- write operations ----------------------------------------------------------------------

    /**
     * Add the specified value to this collection.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param value  the value to store in the collection
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    @Op("+")
    Collection add(Element value)
        {
        throw new ReadOnly();
        }

    /**
     * Add all of the passed values to this collection.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param values  an iterable source providing values to add to this collection
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    @Op("+")
    Collection addAll(Iterable<Element> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered
        Collection result = this;
        for (Element value : values)
            {
            result = result.add(value);
            }
        return result;
        }

    /**
     * Add the specified value to this collection if it is not already present in the collection.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param value  the value to store in the collection
     *
     * @return `True` iff the collection did not contain the specified value, and now does
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    conditional Collection addIfAbsent(Element value)
        {
        if (contains(value))
            {
            return False;
            }

        return True, add(value);
        }

    /**
     * Remove the specified value from this collection.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param value  the value to remove from this collection
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element removal; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    @Op("-")
    Collection remove(Element value)
        {
        throw new ReadOnly();
        }

    /**
     * Remove all of the specified values from this collection.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param values  an iterable source providing values to remove from this collection
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection,
     *         and Boolean True iff the method resulted in a modification
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    @Op("-")
    Collection removeAll(Iterable<Element> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        Collection result = this;
        for (Element value : values)
            {
            result = result.remove(value);
            }
        return result;
        }

    /**
     * Remove the specified value from this collection, reporting back to the caller whether or not
     * the value was found and removed.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param value  the value to remove from this collection
     *
     * @return True iff the specified value existed in the collection, and has been removed
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    conditional Collection removeIfPresent(Element value)
        {
        if (contains(value))
            {
            return True, remove(value);
            }

        return False;
        }

    /**
     * For each value in the collection, evaluate it using the specified function, removing each
     * value for which the specified function evaluates to `True`.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param shouldRemove  a function used to filter this collection, returning `False` for each
     *                      value of this collection that should be removed
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection
     * @return the number of elements removed
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
     (Collection, Int) removeIf(function Boolean (Element) shouldRemove)
        {
        Element[]? values = Null;
        for (Element value : this)
            {
            if (shouldRemove(value))
                {
                values = (values ?: new Element[]) + value;
                }
            }

        if (values == Null)
            {
            return this, 0;
            }

        return removeAll(values), values.size;
        }

    /**
     * Remove all of the values from this collection that do **not** occur in the specified
     * iterable source.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @param values  an iterable source providing values to retain in this collection
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    Collection retainAll(Iterable<Element> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        return removeIf(value -> !values.contains(value));
        }

    /**
     * Remove all values from the collection.
     *
     * A `Mutable` collection will perform the operation in place; persistent collections will
     * return a new collection that reflects the requested changes.
     *
     * @return the resultant collection, which is the same as `this` for a mutable collection
     *
     * @throws ReadOnly  if the collection does not support element addition; among other reasons,
     *                   this will occur if `mutability==Fixed`
     */
    Collection clear()
        {
        // this naive implementation is likely to be overridden for obvious reasons
        return removeIf(value -> True);
        }


    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two collections are equal iff they are they contain the same values.
     */
    static <CompileType extends Collection>
            Boolean equals(CompileType collection1, CompileType collection2)
        {
        // they must be of the same arity
        if (collection1.size != collection2.size)
            {
            return False;
            }

        if (collection1.sortedBy() || collection2.sortedBy())
            {
            // if either is sorted, then both must be of the same order;
            // the collections were of the same arity, so the second iterator shouldn't run out
            // before the first
            Iterator iter1 = collection1.iterator();
            Iterator iter2 = collection2.iterator();
            for (val value1 : iter1)
                {
                assert val value2 := iter2.next();
                if (value1 != value2)
                    {
                    return False;
                    }
                }

            // the collections were of the same arity, so the first iterator shouldn't run out
            // before the second
            assert !iter2.next();
            return True;
            }
        else
            {
            return collection1.containsAll(collection2);
            }
        }
    }
