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
 *   {@link ConstAble} interface.
 */
interface Collection<ElementType>
        extends Iterable<ElementType>
        extends VariablyMutable
    {
    // ----- read operations -----------------------------------------------------------------------

    /**
     * Metadata: Is the collection limited to a distinct set of values?
     */
    @RO Boolean distinct.get()
        {
        return false;
        }

    /**
     * Metadata: Is the collection maintained in an order that is a function of the elements in the
     * collection? And if so, what is the Comparator that represents that ordering?
     */
    conditional Comparator sortedBy()
        {
        return false;
        }

    /**
     * Determine the size of the Collection, which is the number of elements in the Collection.
     */
    @RO Int size;

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
     * Determine if the collection contains the specified value.
     *
     * @param value  the value to search for in this collection
     *
     * @return {@code true} iff the specified value exists in this collection
     */
    Boolean contains(ElementType value)
        {
        // this should be overridden by any implementation that has a structure that can do better
        // than an O(n) search, such as a sorted structure (binary search) or a hashed structure
        return iterator().untilAny(element -> element == value);
        }

    /**
     * Determine if the collection contains all of the specified values.
     *
     * @param values  another collection containing values to search for in this collection
     *
     * @return {@code true} iff the specified values all exist in this collection
     */
    Boolean containsAll(Collection!<ElementType> values)
        {
        return values.iterator().whileEach(&contains(?));
        }

    /**
     * Obtain an Iterator that will iterate over the contents of this Collection.
     *
     * @return an Iterator that will iterate over the contents of this Collection
     */
    @Override
    Iterator<ElementType> iterator();

    /**
     * Obtain a Stream over the contents of this Collection.
     *
     * @return a Stream over the contents of this Collection
     */
    Stream<ElementType> stream();

    /**
     * Obtain a shallow clone of the Collection. The definition of "shallow clone" is that the new
     * collection will contain the same element references, but changes made to this collection
     * through the Collection interface will not appear in the returned collection, nor will changes
     * made to the returned collection through the Collection interface appear in this collection.
     *
     * @return a Collection such that changes to this Collection do not appear in that collection,
     *         nor vice versa
     */
    Collection<ElementType> clone();

    // ----- write operations ----------------------------------------------------------------------

    /**
     * Add the specified value to this collection.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @param value  the value to store in the collection
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    @Op conditional Collection<ElementType> add(ElementType value)
        {
        TODO element addition is not supported
        }

    /**
     * Add all of the passed values to this collection.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @param values  another collection containing values to add to this collection
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    @Op("+") conditional Collection<ElementType> addAll(Collection!<ElementType> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        Collection<ElementType> result   = this;
        Boolean                 modified = false;
        for (ElementType value : values)
            {
            if (result : add(value))
                {
                modified = true;
                }
            }
        return modified, result;
        }

    /**
     * Remove the specified value from this collection.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @param value  the value to remove from this collection
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    @Op("-") conditional Collection<ElementType> remove(ElementType value)
        {
        TODO element removal is not supported
        }

    /**
     * Remove all of the specified values from this collection.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @param values  another collection containing values to remove from this collection
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    @Op("-") conditional Collection<ElementType> removeAll(Collection!<ElementType> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        Collection<ElementType> result   = this;
        Boolean                 modified = false;
        for (ElementType value : values)
            {
            if (result : remove(value))
                {
                modified = true;
                }
            }
        return modified, result;
        }

    /**
     * For each value in the collection, evaluate it using the specified function, removing each
     * value for which the specified function evaluates to {@code true}.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @param shouldRemove  a function used to filter this collection, returning true for each value
     *                      of this collection that should be removed by this method
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    conditional Collection<ElementType> removeIf(function Boolean (ElementType) shouldRemove)
        {
        // this naive implementation does require that the iterator be stable despite removes
        // occurring during iteration; it must obviously be overridden for non-mutable collections
        Collection<ElementType> result   = this;
        Boolean                 modified = false;
        for (ElementType value : this)
            {
            if (shouldRemove(value))
                {
                if (result : remove(value))
                    {
                    modified = true;
                    }
                }
            }
        return modified, result;
        }

    /**
     * Remove all of the values from this collection that do not occur in the specified collection.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @param values  another collection containing values to retain in this collection
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    conditional Collection<ElementType> retainAll(Collection!<ElementType> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        return removeIf(value -> !values.contains(value));
        }

    /**
     * Remove all values from the collection.
     *
     * A _mutable_ collection will perform the operation in place; all other modes of collection
     * will return a new collection that reflects the requested changes.
     *
     * @return the resultant collection, which is the same as {@code this} for a mutable collection,
     *         and Boolean true iff the method resulted in a modification
     */
    conditional Collection<ElementType> clear()
        {
        // this naive implementation is likely to be overridden for obvious reasons
        return removeIf(value -> true);
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
            return false;
            }

        if (collection1.sortedBy() || collection2.sortedBy())
            {
            // if either is sorted, then both must be of the same order;
            // the collections were of the same arity, so the second iterator shouldn't run out
            // before the first
            for (CompileType.ElementType value1 : collection1.iterator(),
                 CompileType.ElementType value2 : collection2.iterator())
                {
                if (value1 != value2)
                    {
                    return false;
                    }
                }

            // the collections were of the same arity, so the first iterator shouldn't run out
            // before the second
            assert !iter2.next();
            return true;
            }
        else
            {
            return collection1.containsAll(collection2);
            }
        }
    }
