/**
 * The List interface represents the intersection of the collection capabilities with the sequence
 * capabilities: an ordered sequence of values.
 *
 * TODO "class ReverseList(List list)"
 * TODO "class RandomizedList(List list)"
 * TODO functions for different sort implementations; default sort() to pick one based on size etc.
 * TODO use binary search for contains/indexOf if there is a Comparator
 */
interface List<ElementType>
        extends Sequence<ElementType>
        extends Collection<ElementType>
    {
    /**
     * Obtain a list cursor that is located at the specified index.
     *
     * @param index  the index within the list to position the cursor; (optional, defaults to the
     *               beginning of the list)
     *
     * @return a new Cursor positioned at the specified index in the List
     *
     * @throws OutOfBounds  if the specified index is outside of range {@code 0} (inclusive) to
     *                      {@code size} (inclusive)
     */
    Cursor cursorAt(Int index = 0)
        {
        return new IndexCursor(this, index); // TODO GG: should be able to infer <ElementType>
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
     * @throws OutOfBounds  if the specified index is outside of range {@code 0} (inclusive) to
     *                      {@code size} (inclusive)
     */
    List insert(Int index, ElementType value)
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
     * @throws OutOfBounds  if the specified index is outside of range {@code 0} (inclusive) to
     *                      {@code size} (inclusive)
     */
    List insertAll(Int index, Sequence<ElementType> | Collection<ElementType> values)
        {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to insert multiple elements efficiently
        Int  i      = index;
        List result = this;
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
     * @throws OutOfBounds  if the specified index is outside of range {@code 0} (inclusive) to
     *                      {@code size} (exclusive)
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
     * @param index  the index of the element to delete, which must be between {@code 0} (inclusive)
     *               and {@code size} (exclusive)
     *
     * @return the resultant list, which is the same as {@code this} for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range {@code 0} (inclusive) to
     *                      {@code size} (exclusive)
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
     * Sort the contents of the list by the specified Comparator.
     *
     * @param comparator  the Comparator to use to sort the list; (optional, defaulting to using the
     *                    "natural" sort order of the ElementType)
     *
     * @throws
     */
    List sort(Comparator<ElementType>? comparator = null)
        {
        // this implementation must be overridden; it assumes a mutable implementation of List, and
        // it is a bubble sort

        Int extent = size;
        if (extent < 2)
            {
            return this;
            }

        function Ordered (ElementType, ElementType) compare;

        if (comparator == null)
            {
            assert ElementType.is(Type<Orderable>);
            compare = (v1, v2) -> v1 <=> v2;
            }
        else
            {
            compare = comparator.compareForOrder;
            }

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

        return this;
        }


    // ----- List Cursor ---------------------------------------------------------------------------

    /**
     * A Cursor is a stateful, mutable navigator of a List. It can be used to walk through a list in
     * either direction, to randomly-access the list by altering its {@link index}, can replace the
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
         * The current index of the cursor within the list, which is a value between {@code 0}
         * (inclusive) and {@code size} (inclusive). If the index is equal to {@code size}, then the
         * cursor is "beyond the end of the list", and refers to a non-existent element.
         *
         * @throws OutOfBounds  if an attempt is made to set the index to a position less than
         *                      {@code 0} or more than {@code size}
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
         * @throws ReadOnly     if the List is not _mutable_ or _fixed-size_
         * @throws OutOfBounds  if an attempt is made to access the value when the cursor is
         *                      beyond the end of the list
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
         * @throws ReadOnly  if the List is not _mutable_
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
         * @throws ReadOnly     if the List is not _mutable_
         * @throws OutOfBounds  if an attempt is made to delete the value when the cursor is
         *                      beyond the end of the list
         */
        void delete();
        }
    }