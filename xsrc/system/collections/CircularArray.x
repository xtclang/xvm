/**
 * A circular array is one that wraps around the end and goes back to the beginning (if that space
 * is not used.) This makes both insertion and deletion from both the beginning and the end of the
 * data structure very efficient.
 *
 * This implementation is a circular list on top of an array. An array is selected as an underlying
 * data structure for both its space efficiency and its random access and update efficiency, O(1).
 * Initially the "head" and the "tail" of the structure are at index `0` in the array, and the
 * "head" of the structure advances with each `add`. As items are removed in FIFO order, the tail
 * advances towards the head. When the head reaches the end of the array, and if the tail is no
 * longer at the start of the array, then the head "wraps around" to the front of the array,
 * seemingly chasing the tail. Similarly, when the tail reaches the end of the array, it too "wraps
 * around" to the front of the array, once again appearing to chase the head. At the point when the
 * head catches up to the tail, the underlying array size must be increased.
 */
class CircularArray<ElementType>
        implements List<ElementType>
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
        contents = new ElementType?[minCapacityFor(initialCapacity)];
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * An array that holds the elements of the CircularArray
     */
    protected/private ElementType?[] contents.set(ElementType?[] array)
        {
        assert array.size.bitCount == 1;
        super(array);
        }

    /**
     * The index of the next element to add at the `head`. The index starts at zero, and is never
     * reset, so the index into the `contents` array is the modulo of the `head` and the size of
     * the `contents` array. The size of the CircularArray is `head-tail`.
     *
     * In theory, the head can be negative, because it is possible to insert before the start of
     * the array, and then to delete from the end (the head) of the array.
     */
    protected/private Int head;

    /**
     * The index of the next element to remove from the `tail`. The index starts at zero, and is
     * never reset, so the index into the `contents` array is the modulo of the `tail` and the size
     * of the `contents` array. For any value of `head`, `tail<=head`. When `tail==head`, the
     * CircularArray is empty.
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

        ElementType?[] newContents = new ElementType?[newSize];
        ElementType?[] oldContents = contents;
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
        return (elements * 2 - 1).leftmostBit.maxOf(16);
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

    /**
     * Verify that the specified index is in the range 0 (inclusive) to `size` (exclusive).
     *
     * @throws OutOfBounds  if the index is not valid
     *
     * @return True
     */
    protected Boolean validateIndex(Int index)
        {
        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        return True;
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
     * Indicate to the CircularArray that a certain number of additional elements are likely to be
     * added or inserted.
     *
     * @param count  an indicator of an expected required capacity beyond the amount *utilized* thus
     *               far
     *
     * @return this
     */
    CircularArray ensureCapacity(Int count)
        {
        Int newSize = size + count;
        if (newSize > capacity)
            {
            adjustCapacity(minCapacityFor(newSize));
            }

        return this;
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


    // ----- VariablyMutable interface -------------------------------------------------------------

    @Override
    Mutability mutability.get()
        {
        return Mutable;
        }


    // ----- UniformIndexed interface --------------------------------------------------------------

    @Override
    @Op("[]")
    ElementType getElement(Int index)
        {
        return contents[indexFor(index)].as(ElementType);
        }

    @Override
    @Op("[]=")
    void setElement(Int index, ElementType value)
        {
        contents[indexFor(index)] = value;
        }

    @Override
    Var<ElementType> elementAt(Int index)
        {
        TODO
        }


    // ----- Sequence interface --------------------------------------------------------------------

    @Override
    @Op("[..]")
    CircularArray slice(Range<Int> range)
        {
        TODO
        }

    @Override
    CircularArray reify()
        {
        return this;
        }


    // ----- List interface ------------------------------------------------------------------------

    @Override
    CircularArray insert(Int index, ElementType value)
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
            validateIndex(index);
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
    CircularArray insertAll(Int index, Iterable<ElementType> values)
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
            assert ElementType value : values.iterator().next();
            return insert(index, value);
            }

        ensureCapacity(additional);
        Int mask = contents.size - 1;
        if (index == 0)
            {
            Int insertAt = tail - additional;
            for (ElementType value : values)
                {
                contents[insertAt++ & mask] = value;
                }
            assert insertAt == tail;
            tail -= additional;
            }
        else
            {
            validateIndex(index);
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
            for (ElementType value : values)
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
        validateIndex(index);
        if (index == 0)
            {
            contents[indexFor(tail++)] = null;
            }
        else if (index == size - 1)
            {
            contents[indexFor(--head)] = null;
            }
        else
            {
            Int mask = contents.size - 1;
            if (index < (size >>> 1))
                {
                // move the tail forward
                for (Int iCopy = tail + index - 1; iCopy >= tail; --iCopy)
                    {
                    contents[iCopy+1 & mask] = contents[iCopy & mask];
                    }
                contents[tail++ & mask] = Null;
                }
            else
                {
                // move the head backwards
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
    CircularArray delete(Range<Int> range)
        {
        validateIndex(range.lowerBound);
        validateIndex(range.upperBound);

        Int removing = range.size;
        if (removing == size)
            {
            clear();
            return this;
            }
        else if (removing == 1)
            {
            return delete(range.lowerBound);
            }

        Int     mask = contents.size - 1;
        Boolean fromTail;
        if (range.lowerBound == 0)
            {
            fromTail = True;
            }
        else if (range.upperBound == size-1)
            {
            fromTail = False;
            }
        else
            {
            fromTail = range.lowerBound < (size - removing >>> 1);
            if (fromTail)
                {
                // move the tail forward
                for (Int iCopy = tail + range.lowerBound - 1; iCopy >= tail; --iCopy)
                    {
                    contents[iCopy+removing & mask] = contents[iCopy & mask];
                    }
                }
            else
                {
                // move the head backwards
                for (Int iCopy = tail + range.upperBound + 1, Int iLast = head - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-1 & mask] = contents[iCopy & mask];
                    }
                }
            }

        if (fromTail)
            {
            while (removing-- > 0)
                {
                contents[tail++ & mask] == Null;
                }
            }
        else
            {
            while (removing-- > 0)
                {
                contents[--head & mask] == Null;
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
    Iterator<ElementType> iterator()
        {
        return new Iterator()
            {
            Int prevTail = tail;
            Int prevHead = head;
            Int index    = tail;

            @Override
            conditional ElementType next()
                {
                // adjust for a single deletion
                if (prevTail != tail || prevHead != head)
                    {
                    if (prevTail == tail && prevHead-1 == head)
                        {
                        prevHead = head;
                        --index;
                        }
                    else if (prevTail+1 == tail && prevHead == head)
                        {
                        prevTail = tail;
                        }
                    else
                        {
                        throw new ConcurrentModification();
                        }
                    }

                if (index < head)
                    {
                    return True, CircularArray.this[index++];
                    }
                return False;
                }
            };
        }

    @Override
    @Op("+")
    CircularArray add(ElementType value)
        {
        ensureCapacity(1);
        contents[indexFor(head++)] = value;
        return this;
        }

    @Override
    @Op("+")
    CircularArray addAll(Iterable<ElementType> values)
        {
        ensureCapacity(values.size);
        for (ElementType value : values)
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
            // REVIEW do we need Array.fill()???
            // just wipe the existing storage
            while (size > 0)
                {
                delete(0);
                }
            }
        else
            {
            // create a new, empty storage
            contents = new ElementType?[minCapacityFor(0)];
            }

        tail = 0;
        head = 0;

        return this;
        }

    @Override
    CircularArray clone()
        {
        TODO
        }
    }