/**
 * The List interface represents the intersection of the collection capabilities with the sequence
 * capabilities: an ordered sequence of values.
 *
 * TODO a "reverseOrder()" method could be useful
 * TODO a "randomizeOrder()" method could be useful
 * TODO a real sort implementation
 * TODO a binary search if there is a comparator
 */
interface List<ElementType>
        extends Sequence<ElementType>
        extends Collection<ElementType>
    {
    // ----- List Cursor ---------------------------------------------------------------------------

    /**
     * A Cursor is a stateful, mutable navigator of a List. It can be used to walk through a list in
     * either direction, to randomly-access the list by altering its {@link index}, can replace the
     * the values of elements in the list, insert or append new elements into the list, or delete
     * elements from the list.
     */
    interface Cursor<ElementType>
        {
        /**
         * The containing list.
         */
        @RO List<ElementType> list;

        /**
         * The current index of the cursor within the list, which is a value between {@code 0}
         * (inclusive) and {@code size} (inclusive). If the index is equal to {@code size}, then the
         * cursor is "beyond the end of the list", and refers to a non-existent element.
         *
         * @throws BoundsException  if an attempt is made to set the index to a position less than
         *                          {@code 0} or more than {@code size}
         */
        Int index;

        /**
         * Move the cursor so that it points to the _next_ element in the list. If there are no more
         * elements in the list, then the index will be set to {@code size}, and the cursor will be
         * "beyond the end of the list", and referring to a non-existent element.
         *
         * @return true if the cursor has advanced to another element in the list, or false if the
         *         cursor is now beyond the end of the list
         */
        Boolean advance();

        /**
         * Move the cursor so that it points to the _previous_ element in the list. If there are no
         * elements preceding the current element in the list, then the index will be set to
         * {@code 0}, and referring to the first element in the list.
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
         * @throws ReadOnlyException  if the List is not _mutable_ or _fixed-size_
         * @throws BoundsException    if an attempt is made to access the value when the cursor is
         *                            beyond the end of the list
         */
        ElementType value;

        /**
         * Insert the specified element at the current index, shifting the contents of the entire
         * remainder of the list "to the right" as a result. If the index is beyond the end of the
         * list, this operation has the same effect as setting the {@link Cursor.value} property.
         *
         * After this method completes successfully, the cursor will be positioned on the newly
         * inserted element.
         *
         * @throws ReadOnlyException  if the List is not _mutable_
         */
        void insert(ElementType value);

        /**
         * Delete the element at the current index, shifting the contents of the entire remainder of
         * the list "to the left" as a result.
         *
         * After this method completes successfully, the cursor will be positioned on the element
         * that immediately followed the element that was just deleted, or on the non-existent
         * element "beyond the end of the list" if the element deleted was the last element in the
         * list.
         *
         * @throws ReadOnlyException  if the List is not _mutable_
         * @throws BoundsException    if an attempt is made to delete the value when the cursor is
         *                            beyond the end of the list
         */
        void delete();
        }

    /**
     * Obtain a list cursor that is located at the specified index.
     *
     * @param index  the index within the list to position the cursor; (optional, defaults to the
     *               beginning of the list)
     *
     * @return a new Cursor positioned at the specified index in the List
     *
     * @throws BoundsException  if the specified index is outside of range {@code 0} (inclusive) to
     *         {@code size} (inclusive)
     */
    Cursor<ElementType> cursor(Int index = 0)
        {
        return new SimpleCursor<ElementType>(index);
        }

    /**
     * Insert the specified value into the List at the specified index, shifting the contents of the
     * entire remainder of the list as a result. If the index is beyond the end of the list, this
     * operation has the same effect as calling {@link add}.
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient insertion.
     *
     * @param index  the index at which to insert, which must be between {@code 0} (inclusive) and
     *               {@code size} (inclusive)
     * @param value  the value to insert
     *
     * @return the resultant list, which is the same as {@code this} for a mutable list
     *
     * @throws BoundsException  if the specified index is outside of range {@code 0} (inclusive) to
     *         {@code size} (inclusive)
     */
    List<ElementType> insert(Int index, ElementType value)
        {
        TODO element addition is not supported
        }

    /**
     * Insert the specified values into the List at the specified index, shifting the contents of
     * the entire remainder of the list as a result. If the index is beyond the end of the list,
     * this operation has the same effect as calling {@link addAll}.
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient insertion.
     *
     * @param index   the index at which to insert, which must be between {@code 0} (inclusive) and
     *                {@code size} (inclusive)
     * @param values  the values to insert
     *
     * @return the resultant list, which is the same as {@code this} for a mutable list
     *
     * @throws BoundsException  if the specified index is outside of range {@code 0} (inclusive) to
     *         {@code size} (inclusive)
     */
    List<ElementType> insertAll(Int index, Sequence<ElementType> | Collection<ElementType> values)
        {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to insert multiple elements efficiently
        Int i = index;
        List<ElementType> result = this;
        for (ElementType value : values)
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
     * @param index  the index of the element to delete, which must be between {@code 0} (inclusive)
     *               and {@code size} (exclusive)
     *
     * @return the resultant list, which is the same as {@code this} for a mutable list
     *
     * @throws BoundsException  if the specified index is outside of range {@code 0} (inclusive) to
     *         {@code size} (exclusive)
     */
    List<ElementType> delete(Int index)
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
     * @param index  the index of the element to delete, which must be between {@code 0} (inclusive)
     *               and {@code size} (exclusive)
     *
     * @return the resultant list, which is the same as {@code this} for a mutable list
     *
     * @throws BoundsException  if the specified index is outside of range {@code 0} (inclusive) to
     *         {@code size} (exclusive)
     */
    List<ElementType> delete(Range<Int> range)
        {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to delete multiple elements efficiently
        List<ElementType> result = this;
        Int index = range.lowerBound;
        Int count = range.upperBound - index + 1;
        while (count-- > 0)
            {
            result = result.delete(index);
            }
        return result;
        }

    /**
     * Sort the contents of the list by the specified Comparator.
     *
     * @param comparator  the Comparator to use to sort the list; (optional, defaulting to using the
     *                    "natural" sort order of the ElementType)
     *
     * @throws
     */
    List<ElementType> sort(Comparator<ElementType>? comparator = null)
        {
        // this implementation must be overridden; it assumes a mutable implementation of List, and
        // it is a bubble sort

        Int extent = size;
        if (extent < 2)
            {
            return this;
            }

        function Ordered (ElementType, ElementType) compare = comparator?.compareForOrder : (v1, v2) -> v1 <=> v2;

        do
            {
            Boolean     sorted = true;
            ElementType bubble = this[0];
            for (Int i = 1; i < extent; ++i)
                {
                ElementType next = this[i];
                if (compare(bubble, next) == Greater)
                    {
                    this[i-1] = next;
                    this[i]   = bubble;
                    sorted    = false;
                    }
                else
                    {
                    bubble = next;
                    }
                }

            // greatest item in the list bubbled up to the highest index
            --extent;
            }
        while (!sorted);
        }

    // ----- Cursor implementation -----------------------------------------------------------------

    /**
     * A SimpleCursor delegates all operations back to the List that created it.
     */
    class SimpleCursor<ElementType>
            implements Cursor<ElementType>
        {
        /**
         * Construct a SimpleCursor for the containing List.
         *
         * @param index pass {@code -1} to start the cursor before the beginning of the List,
         *        {@code List.size} to start the cursor beyond the end of the List, or any value
         *        between {@code 0} (inclusive) and {@code List.size} (exclusive) to start the
         *        cursor on a specific element (as if the caller had just obtained that element from
         *        a call to {@link Cursor.next next()} or {@link Cursor.prev prev()})
         */
        construct(Int index)
            {
            if (index < 0 || index > List.this.size)
                {
                throw new BoundsException();
                }
            internalIndex = index;
            }

        /**
         * This is the internal property in which the actual index is stored.
         */
        private Int internalIndex;

        @Override
        Int index
            {
            @Override
            Int get()
                {
                return internalIndex.minOf(List.this.size);
                }

            @Override
            void set(Int i)
                {
                if (i < 0 || i > List.this.size)
                    {
                    throw new BoundsException();
                    }
                internalIndex = i;
                }
            }

        @Override
        Boolean advance()
            {
            Int i    = internalIndex + 1;
            Int size = List.this.size;
            internalIndex = i.minOf(size);
            return i < size;
            }

        @Override
        Boolean rewind()
            {
            Int i = internalIndex;
            if (i > 0)
                {
                internalIndex = i - 1;
                return true;
                }
            return false;
            }

        @Override
        ElementType value
            {
            @Override
            ElementType get()
                {
                // may throw BoundsException
                return List.this[index];
                }

            @Override
            void set(ElementType value)
                {
                Int i    = internalIndex;
                Int size = List.this.size;
                if (i < size)
                    {
                    // may throw ReadOnlyException
                    List.this[i] = value;
                    }
                else
                    {
                    if (List<ElementType> newList : List.this.add(value))
                        {
                        assert &newList == &List.this;
                        internalIndex = size;
                        }
                    }
                }
            }

        @Override
        void insert(ElementType value)
            {
            Int i    = internalIndex;
            Int size = List.this.size;
            List<ElementType> newList;
            if (i < size)
                {
                newList = List.this.insert(i, value);
                }
            else
                {
                if (newList : List.this.add(value))
                    {
                    internalIndex = size;
                    }
                }
            assert &newList == &List.this;
            }

        @Override
        void delete()
            {
            Int i = index;
            if (i < List.this.size)
                {
                List<ElementType> newList = List.this.delete(i);
                assert &newList == &List.this;
                }
            }
        }
    }