/**
 * The List interface represents the intersection of the collection capabilities with the sequence
 * capabilities: an ordered sequence of values.
 *
 * TODO "class ReverseList(List list)"
 * TODO "class RandomizedList(List list)"
 * TODO functions for different sort implementations; default sort() to pick one based on size etc.
 * TODO use binary search for contains/indexOf if there is a Comparator
 */
interface List<Element>
        extends Sequence<Element>
        extends Collection<Element>
    {
    /**
     * Obtain the first element in the list.
     *
     * @return True iff the list is not empty
     * @return the first element in the list
     */
    conditional Element first()
        {
        if (empty)
            {
            return False;
            }
        return True, this[0];
        }

    /**
     * Obtain the last element in the list.
     *
     * @return True iff the list is not empty
     * @return the last element in the list
     */
    conditional Element last()
        {
        if (empty)
            {
            return False;
            }
        return True, this[size-1];
        }

    /**
     * Replace the existing value in the List at the specified index with the specified value.
     *
     * @param index  the index at which to store the specified value, which must be between `0`
     *               (inclusive) and `size` (exclusive)
     * @param value  the value to store
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    List replace(Int index, Element value)
        {
        if (mutability.persistent)
            {
            TODO element replacement is not supported
            }
        else
            {
            // mutable and fixed-size arrays are modified "in place"
            this[index] = value;
            return this;
            }
        }

    /**
     * Replace existing values in the List with the provided values, starting at the specified
     * index.
     *
     * @param index  the index at which to store the specified value, which must be between `0`
     *               (inclusive) and `size` (exclusive)
     * @param value  the value to store
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive), or if the specified index plus the size of the
     *                      provided Iterable is greater than the size of this array
     */
    List replaceAll(Int index, Iterable<Element> values)
        {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to replace multiple elements efficiently
        Int  i      = index;
        List result = this;
        for (Element value : values)
            {
            result = result.replace(i++, value);
            }
        return result;
        }

    /**
     * Insert the specified value into the List at the specified index, shifting the contents of the
     * entire remainder of the list as a result. If the index is beyond the end of the list, this
     * operation has the same effect as calling [add].
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient insertion.
     *
     * @param index  the index at which to insert, which must be between `0` (inclusive) and
     *               `size` (inclusive)
     * @param value  the value to insert
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    List insert(Int index, Element value)
        {
        TODO element addition is not supported
        }

    /**
     * Insert the specified values into the List at the specified index, shifting the contents of
     * the entire remainder of the list as a result. If the index is beyond the end of the list,
     * this operation has the same effect as calling [addAll].
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient insertion.
     *
     * @param index   the index at which to insert, which must be between `0` (inclusive) and
     *                `size` (inclusive)
     * @param values  the values to insert
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    List insertAll(Int index, Iterable<Element> values)
        {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to insert multiple elements efficiently
        Int  i      = index;
        List result = this;
        for (Element value : values)
            {
            result = result.insert(i++, value);
            }
        return result;
        }

    /**
     * Delete the element at the specified index, shifting the contents of the entire remainder of
     * the list as a result.
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient deletion.
     *
     * @param index  the index of the element to delete, which must be between `0` (inclusive)
     *               and `size` (exclusive)
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (exclusive)
     */
    List delete(Int index)
        {
        TODO element removal is not supported
        }

    /**
     * Delete the elements within the specified range, shifting the contents of the entire remainder
     * of the list as a result.
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient deletion.
     *
     * @param range  the range of indexes of the elements to delete, which must be between `0`
     *               (inclusive) and `size` (exclusive)
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (exclusive)
     */
    List delete(Range<Int> range)
        {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to delete multiple elements efficiently
        List result = this;
        Int  index  = range.lowerBound;
        Int  count  = range.upperBound - index + 1;
        while (count-- > 0)
            {
            result = result.delete(index);
            }
        return result;
        }

    /**
     * Sort the contents of this list in the order specified by the optional Comparator.
     *
     * @param comparator  the Comparator to use to sort the list; (optional, defaulting to using the
     *                    "natural" sort order of the Element type)
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     */
    List sort(Comparator<Element>? comparator = null)
        {
        if (size <= 1)
            {
            return this;
            }

        // eventual to-do is to should pick a better sort impl based on some heuristics, such as
        // size of list and how many elements are out-of-order
        function void (List<Element>, Comparator<Element>?) sortimpl = bubbleSort;

        Mutability mutability = this.mutability;
        if (!mutability.persistent)
            {
            sortimpl(this, comparator);
            return this;
            }

        List!   temp;
        Boolean inPlace = True;
        if (this.is(FixedSizeAble))
            {
            temp = ensureFixedSize();
            }
        else if (this.is(MutableAble))
            {
            temp = ensureMutable();
            }
        else
            {
            temp    = toArray();
            inPlace = False;
            }

        sortimpl(temp, comparator);

        if (inPlace)
            {
            if (mutability == Persistent && temp.is(PersistentAble))
                {
                return temp.ensurePersistent(True);
                }
            else if (mutability == Constant && temp.is(ImmutableAble))
                {
                return temp.ensureImmutable(True);
                }
            }

        return this.clear().addAll(temp);
        }

    /**
     * Bubble-sort the contents of the passed list using an optional Comparator. The loop is
     * optimized for an almost-sorted list, with the most-likely-to-be-unsorted items at the end.
     *
     * @param list        a Mutable or Fixed list
     * @param comparator  the Comparator to use to sort the list; (optional, defaulting to using the
     *                    "natural" sort order of the Element type)
     */
    static <Element> void bubbleSort(List<Element> list, Comparator<Element>? comparator = null)
        {
        assert !list.mutability.persistent;

        Int last = list.size - 1;
        if (last <= 0)
            {
            return;
            }

        function Ordered (Element, Element) compare;

        if (comparator == null)
            {
            assert Element.is(Type<Orderable>);
            compare = (v1, v2) -> v1 <=> v2;
            }
        else
            {
            compare = comparator.compareForOrder;
            }

        Int first = 0;
        do
            {
            Boolean     sorted = true;
            Element bubble = list[last];
            for (Int i = last-1; i >= first; --i)
                {
                Element prev = list[i];
                if (compare(prev, bubble) == Greater)
                    {
                    list[i  ] = bubble;
                    list[i+1] = prev;
                    sorted    = false;
                    }
                else
                    {
                    bubble = prev;
                    }
                }

            // the smallest item in the list has now bubbled up to the first position in the list;
            // optimize the next iteration by only bubbling up to the second position, and so on
            ++first;
            }
        while (!sorted);
        }


    // ----- List Cursor ---------------------------------------------------------------------------

    /**
     * Obtain a list cursor that is located at the specified index.
     *
     * @param index  the index within the list to position the cursor; (optional, defaults to the
     *               beginning of the list)
     *
     * @return a new Cursor positioned at the specified index in the List
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    Cursor cursorAt(Int index = 0)
        {
        return new IndexCursor(index);
        }

    /**
     * A Cursor is a stateful, mutable navigator of a List. It can be used to walk through a list in
     * either direction, to randomly-access the list by altering its [index], can replace the
     * the values of elements in the list, insert or append new elements into the list, or delete
     * elements from the list.
     */
    interface Cursor
        {
        /**
         * The containing list.
         */
        @RO List list;

        /**
         * The current index of the cursor within the list, which is a value between `0`
         * (inclusive) and `size` (inclusive). If the index is equal to `size`, then the
         * cursor is "beyond the end of the list", and refers to a non-existent element.
         *
         * @throws OutOfBounds  if an attempt is made to set the index to a position less than
         *                      `0` or more than `size`
         */
        Int index;

        /**
         * True iff the index is less than the list size.
         */
        @RO Boolean exists;

        /**
         * Move the cursor so that it points to the _next_ element in the list. If there are no more
         * elements in the list, then the index will be set to `size`, and the cursor will be
         * "beyond the end of the list", and referring to a non-existent element.
         *
         * @return true if the cursor has advanced to another element in the list, or false if the
         *         cursor is now beyond the end of the list
         */
        Boolean advance();

        /**
         * Move the cursor so that it points to the _previous_ element in the list. If there are no
         * elements preceding the current element in the list, then the index will be set to
         * `0`, and referring to the first element in the list.
         *
         * @return true if the cursor has rewound to another element in the list, or false if the
         *         cursor was already at the beginning of the list
         */
        Boolean rewind();

        /**
         * This is the value of the element at the current index in the list. If the index is beyond
         * the end of the list, then the value cannot be accessed (an attempt will raise an
         * exception), but _setting_ the value is legal, and will append the specified value to the
         * end of the list.
         *
         * @throws ReadOnly     if the List is not _mutable_ or _fixed-size_
         * @throws OutOfBounds  if an attempt is made to access the value when the cursor is
         *                      beyond the end of the list
         */
        Element value;

        /**
         * Insert the specified element at the current index, shifting the contents of the entire
         * remainder of the list "to the right" as a result. If the index is beyond the end of the
         * list, this operation has the same effect as setting the [Cursor.value] property.
         *
         * After this method completes successfully, the cursor will be positioned on the newly
         * inserted element.
         *
         * @throws ReadOnly  if the List is not _mutable_
         */
        void insert(Element value);

        /**
         * Delete the element at the current index, shifting the contents of the entire remainder of
         * the list "to the left" as a result.
         *
         * After this method completes successfully, the cursor will be positioned on the element
         * that immediately followed the element that was just deleted, or on the non-existent
         * element "beyond the end of the list" if the element deleted was the last element in the
         * list.
         *
         * @throws ReadOnly     if the List is not _mutable_
         * @throws OutOfBounds  if an attempt is made to delete the value when the cursor is
         *                      beyond the end of the list
         */
        void delete();
        }

    /**
     * An IndexCursor is a simple [List.Cursor] implementation that delegates all operations back to
     * a List using the index-based operations on the List itself.
     */
    class IndexCursor
            implements Cursor
        {
        /**
         * Construct an IndexCursor for the specified List.
         *
         * @param index  the location of the cursor in the list, between `0` (inclusive) and [List.size]
         *               (exclusive)
         */
        construct(Int index = 0)
            {
            if (index < 0 || index > size)
                {
                throw new OutOfBounds();
                }

            this.index = index;
            }

        @Override
        List<Element> list.get()
            {
            return this.List;
            }

        @Override
        Int index
            {
            @Override
            Int get()
                {
                return super().minOf(size);
                }

            @Override
            void set(Int i)
                {
                if (i < 0 || i > size)
                    {
                    throw new OutOfBounds();
                    }
                super(i);
                }
            }

        @Override
        Boolean exists.get()
            {
            return index < size;
            }

        @Override
        Boolean advance()
            {
            Int next = index + 1;
            index = next.minOf(size);
            return next < size;
            }

        @Override
        Boolean rewind()
            {
            Int prev = index;
            if (prev > 0)
                {
                index = prev - 1;
                return true;
                }
            return false;
            }

        @Override
        Element value
            {
            @Override
            Element get()
                {
                // may throw OutOfBounds
                return list[index];
                }

            @Override
            void set(Element value)
                {
                Int index = this.index;
                if (index < size)
                    {
                    // may throw ReadOnly
                    list[index] = value;
                    }
                else if (mutability == Mutable)
                    {
                    add(value);
                    index = size;
                    }
                else
                    {
                    throw new ReadOnly();
                    }
                }
            }

        @Override
        void insert(Element value)
            {
            if (mutability != Mutable)
                {
                throw new ReadOnly();
                }

            Int index = this.index;
            if (index < size)
                {
                list.insert(index, value);
                }
            else
                {
                add(value);
                }
            }

        @Override
        void delete()
            {
            if (mutability != Mutable)
                {
                throw new ReadOnly();
                }

            Int index = this.index;
            if (index < size)
                {
                list.delete(index);
                }
            }
        }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    @Op("-")
    List remove(Element value)
        {
        if (Int index := indexOf(value))
            {
            return delete(index);
            }

        return this;
        }

    @Override
    conditional List removeIfPresent(Element value)
        {
        if (Int index := indexOf(value))
            {
            return True, delete(index);
            }

        return False;
        }

    @Override
    (List, Int) removeIf(function Boolean (Element) shouldRemove)
        {
        List<Element> result = this;

        Int index = 0;
        Int size  = this.size;
        Int count = 0;
        while (index < size)
            {
            if (shouldRemove(this[index]))
                {
                result = result.delete(index);
                --size;
                ++count;
                }
            else
                {
                ++index;
                }
            }

        return result, count;
        }


    // ----- Equality ------------------------------------------------------------------------------

    /**
     * Two lists are equal iff they are of the same size, and they contain the same values, in the
     * same order.
     */
    static <CompileType extends List> Boolean equals(CompileType a1, CompileType a2)
        {
        Int c = a1.size;
        if (c != a2.size)
            {
            return False;
            }

        for (Int i = 0; i < c; ++i)
            {
            if (a1[i] != a2[i])
                {
                return False;
                }
            }

        return True;
        }
    }