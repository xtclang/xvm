/**
 * A circular array is one that wraps around the end and goes back to the beginning (if that space
 * is not used.) This makes both insertion and deletion from both the beginning and the end of the
 * data structure very efficient.
 *
 * This implementation is a circular list on top of an array. An array is selected as an underlying
 * data structure for both its space efficiency and its random access and update efficiency, `O(1)`.
 * Initially the "head" and the "tail" of the structure are at index `0` in the array, and the
 * "head" of the structure advances with each `add`. As items are removed in FIFO order, the tail
 * advances towards the head. When the head reaches the end of the array, and if the tail is no
 * longer at the start of the array, then the head "wraps around" to the front of the array in order
 * to store added items, seemingly chasing the tail. (In reality, the head is modulo'd by the size
 * of the array to obtain a storage location.) Similarly, when the tail reaches the end of the
 * array, it too "wraps around" (via the same modulo function) to the front of the array, once again
 * appearing to chase the head. At the point when the tail catches up to the head, the array is
 * "empty", and when the head catches up to the tail, the underlying array size must be increased in
 * order to add more elements.
 */
class CircularArray<Element>
        implements List<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an CircularArray with an initial capacity.
     *
     * @param initialCapacity  the number of elements to initially allocate storage for
     */
    construct(Int initialCapacity = 0)
        {
        // calculate the smallest power of 2 greater than the specified initial capacity
        contents = new Element?[minCapacityFor(initialCapacity)];
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * An array that holds the elements of the CircularArray
     */
    protected/private Element?[] contents.set(Element?[] array)
        {
        assert array.size.bitCount == 1;
        super(array);
        }

    /**
     * The index of the next element to add at the `head`. The index starts at zero, and is never
     * reset, so the index into the underlying `contents` array is the modulo of the `head` and the
     * size of the `contents` array. The size of the CircularArray is `head-tail`.
     *
     * In theory, the head can be negative, because it is possible to insert before the start of
     * the array, and then to delete from the end (the head) of the array.
     */
    protected/private Int head;

    /**
     * The index of the next element to remove from the `tail`. The index starts at zero, and is
     * never reset, so the index into the `contents` array is the modulo of the `tail` and the size
     * of the underlying `contents` array. For any value of `head`, `tail<=head`. The size of the
     * CircularArray is `head-tail`, and when `tail==head`, the CircularArray is empty.
     *
     * In theory, the tail can be negative, because it is possible to insert before the start of
     * the array.
     */
    protected/private Int tail;

    /**
     * Reallocate the internal storage of the CircularArray.
     *
     * @param newSize  the number of desired elements (a power of 2)
     */
    private void adjustCapacity(Int newSize)
        {
        assert newSize.bitCount == 1;

        Element?[] newContents = new Element?[newSize];
        Element?[] oldContents = contents;
        Int oldSize = contents.size;
        assert newSize != oldSize;

        Int oldMask = oldSize - 1;
        Int newMask = newSize - 1;
        for (Int i = tail; i < head; ++i)
            {
            newContents[i & newMask] = oldContents[i & oldMask];
            }

        this.contents = newContents;
        }

    /**
     * Calculate a minimum capacity to allocate to hold the specified number of elements.
     *
     * @param elements  the number of elements to hold
     *
     * @return the number of elements to allocate
     */
    protected static Int minCapacityFor(Int elements)
        {
        assert elements >= 0;
        return (elements * 2 - 1).maxOf(0).leftmostBit.maxOf(16);
        }

    /**
     * Given an absolute index such as the tail and head indexes, determine the corresponding index
     * in the contents array.
     *
     * @param index an index such as `tail` or `head`
     *
     * @return a corresponding index into the contents array
     */
    protected Int indexFor(Int index)
        {
        // contents must be of a power-of-two size
        return index & contents.size-1;
        }


    // ----- public interface ----------------------------------------------------------------------

    /**
     * The allocated storage capacity of the CircularArray.
     */
    Int capacity.get()
        {
        return contents.size;
        }

    /**
     * If there is unused capacity in the CircularArray, then release as much of that excess capacity
     * as possible.
     *
     * @return the CircularArray
     */
    CircularArray trimCapacity()
        {
        Int oldCap = capacity;
        Int newCap = minCapacityFor(size);
        if (newCap < oldCap)
            {
            adjustCapacity(newCap);
            }

        return this;
        }


    // ----- UniformIndexed interface --------------------------------------------------------------

    @Override
    @Op("[]")
    Element getElement(Int index)
        {
        assert:bounds 0 <= index < size;
        return contents[indexFor(tail+index)].as(Element);
        }

    @Override
    @Op("[]=")
    void setElement(Int index, Element value)
        {
        assert:bounds 0 <= index < size;
        contents[indexFor(tail+index)] = value;
        }


    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") CircularArray slice(Range<Int> indexes) // TODO this could just return a ListSlice instance
        {
        TODO
        }


    // ----- List interface ------------------------------------------------------------------------

    @Override
    CircularArray insert(Int index, Element value)
        {
        if (index == size)
            {
            return add(value);
            }

        ensureCapacity(1);
        if (index == 0)
            {
            contents[indexFor(--tail)] = value;
            }
        else
            {
            assert:bounds 0 < index < size;
            Int mask = contents.size - 1;
            if (index < (size >>> 1))
                {
                // move the tail
                for (Int iCopy = tail, Int iLast = tail + index - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-1 & mask] = contents[iCopy & mask];
                    }
                --tail;
                }
            else
                {
                // move the head
                for (Int iCopy = head - 1, Int iLast = tail + index; iCopy >= iLast; --iCopy)
                    {
                    contents[iCopy+1 & mask] = contents[iCopy & mask];
                    }
                ++head;
                }
            contents[tail+index & mask] = value;
            }

        return this;
        }

    @Override
    CircularArray insertAll(Int index, Iterable<Element> values)
        {
        if (index == size)
            {
            return addAll(values);
            }

        Int additional = values.size;
        if (additional == 0)
            {
            return this;
            }
        else if (additional == 1)
            {
            assert Element value := values.iterator().next();
            return insert(index, value);
            }

        ensureCapacity(additional);
        Int mask = contents.size - 1;
        if (index == 0)
            {
            Int insertAt = tail - additional;
            for (Element value : values)
                {
                contents[insertAt++ & mask] = value;
                }
            assert insertAt == tail;
            tail -= additional;
            }
        else
            {
            assert:bounds 0 < index < size;
            if (index < (size >>> 1))
                {
                // move the tail
                for (Int iCopy = tail, Int iLast = tail + index - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-additional & mask] = contents[iCopy & mask];
                    }
                tail -= additional;
                }
            else
                {
                // move the head
                for (Int iCopy = head - 1, Int iLast = tail + index; iCopy >= iLast; --iCopy)
                    {
                    contents[iCopy+additional & mask] = contents[iCopy & mask];
                    }
                head += additional;
                }

            Int insertAt = tail + index;
            for (Element value : values)
                {
                contents[insertAt++ & mask] = value;
                }
            assert insertAt == tail + index + additional;
            }

        return this;
        }

    @Override
    CircularArray delete(Int index)
        {
        assert:bounds 0 <= index < size;
        if (index == 0)
            {
            contents[indexFor(tail++)] = Null;
            }
        else if (index == size - 1)
            {
            contents[indexFor(--head)] = Null;
            }
        else
            {
            Int mask = contents.size - 1;
            if (index < (size >>> 1))
                {
                // move the tail forward (but copy the sliding elements right to left)
                for (Int iCopy = tail + index - 1; iCopy >= tail; --iCopy)
                    {
                    contents[iCopy+1 & mask] = contents[iCopy & mask];
                    }
                contents[tail++ & mask] = Null;
                }
            else
                {
                // move the head backwards (but copy the sliding elements left to right)
                for (Int iCopy = tail + index + 1, Int iLast = head - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-1 & mask] = contents[iCopy & mask];
                    }
                contents[--head & mask] = Null;
                }
            }

        return this;
        }

    @Override
    CircularArray deleteAll(Interval<Int> indexes)
        {
        Int lo = indexes.effectiveLowerBound;
        Int hi = indexes.effectiveUpperBound;
        switch (lo <=> hi)
            {
            case Lesser:
                break;

            case Equal:
                return delete(lo);

            case Greater:
                // e.g. a range like [3..3)
                return this;
            }

        Int size = size;
        assert:bounds 0 <= lo < hi < size;

        Int removing = hi - lo + 1;
        if (removing == size)
            {
            return clear();
            }

        // determine whether the deletions should occur from the head or from the tail
        Int     mask = contents.size - 1;
        Boolean fromHead;
        if (lo == 0)
            {
            // delete from the tail; the head will not move
            fromHead = False;
            }
        else if (hi == size-1)
            {
            // delete from the head; the tail will not move
            fromHead = True;
            }
        else
            {
            // stuff is getting removed from somewhere the middle; figure out which end to remove
            // from in order to move the least number of elements
            Int retainTail = lo;
            Int retainHead = size - hi - 1;
            fromHead = retainTail > retainHead;
            if (fromHead)
                {
                // move the head backwards (but copy the sliding elements left to right)
                for (Int copy = tail + hi + 1, Int last = head - 1; copy <= last; ++copy)
                    {
                    contents[copy-removing & mask] = contents[copy & mask];
                    }
                }
            else
                {
                // slide the tail forward (but copy the sliding elements right to left)
                for (Int copy = tail + lo - 1; copy >= tail; --copy)
                    {
                    contents[copy+removing & mask] = contents[copy & mask];
                    }
                }
            }

        if (fromHead)
            {
            while (--removing >= 0)
                {
                contents[--head & mask] == Null;
                }
            }
        else
            {
            while (--removing >= 0)
                {
                contents[tail++ & mask] == Null;
                }
            }

        return this;
        }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    @RO Int size.get()
        {
        return head - tail;
        }

    @Override
    @RO Boolean empty.get()
        {
        return head == tail;
        }

    @Override
    Iterator<Element> iterator()
        {
        return new Iterator()
            {
            Int index    = tail;
            Int prevTail = tail;
            Int prevHead = head;

            @Override
            conditional Element next()
                {
                // adjust for a single deletion
                if (prevTail != tail)
                    {
                    if (prevTail+1 == tail && prevHead == head && index >= tail)
                        {
                        // the last iterated thing was deleted
                        prevTail = tail;
                        }
                    else
                        {
                        throw new ConcurrentModification();
                        }
                    }
                else if (prevHead != head)
                    {
                    if (prevHead-1 == head)
                        {
                        // the end of the array was deleted
                        prevHead = head;
                        }
                    else
                        {
                        throw new ConcurrentModification();
                        }
                    }

                if (index < head)
                    {
                    return True, this.CircularArray[index++ - tail];
                    }

                return False;
                }
            };
        }

    @Override
    @Op("+")
    CircularArray add(Element value)
        {
        ensureCapacity(1);
        contents[indexFor(head++)] = value;
        return this;
        }

    @Override
    @Op("+")
    CircularArray addAll(Iterable<Element> values)
        {
        ensureCapacity(values.size);
        for (Element value : values)
            {
            contents[indexFor(head++)] = value;
            }
        return this;
        }

    @Override
    CircularArray clear()
        {
        if (capacity <= 16)
            {
            // just wipe the existing storage
            contents.fill(Null);
            }
        else
            {
            // create a new, empty storage
            contents = new Element?[minCapacityFor(0)];
            }

        tail = 0;
        head = 0;

        return this;
        }


    // ----- Appender interface --------------------------------------------------------------------

    /**
     * Indicate to the CircularArray that a certain number of additional elements are likely to be
     * added or inserted.
     *
     * @param additional  an indicator of an expected required capacity beyond the amount
     *                    **utilized** thus far
     *
     * @return this
     */
    @Override
    CircularArray ensureCapacity(Int additional)
        {
        Int newSize = size + additional;
        if (newSize > capacity)
            {
            adjustCapacity(minCapacityFor(newSize));
            }

        return this;
        }
    }