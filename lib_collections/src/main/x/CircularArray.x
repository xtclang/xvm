/**
 * A circular array is one that wraps around the end and goes back to the beginning (if that space
 * is not used.) This makes both insertion and deletion from both the beginning and the end of the
 * data structure very efficient.
 *
 * This implementation is a circular list on top of an array. An array is selected as an underlying
 * data structure for both its space efficiency and its random access and update efficiency, O(1).
 * Initially the "tail" and the "head" of the structure are at index `0` in the array, and the
 * "tail" of the structure advances with each `add`. As items are removed in FIFO order, the head
 * advances towards the tail. When the tail reaches the end of the array, and if the head is no
 * longer at the start of the array, then the tail "wraps around" to the front of the array,
 * seemingly chasing the head. Similarly, when the head reaches the end of the array, it too "wraps
 * around" to the front of the array, once again appearing to chase the tail. At the point when the
 * tail catches up to the head, the underlying array size must be increased.
 */
@Concurrent
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
     * The index of the next element to add at the `tail`. The index starts at zero, and is never
     * reset, so the index into the `contents` array is the modulo of the `tail` and the size of
     * the `contents` array. The size of the CircularArray is `tail-head`.
     *
     * In theory, the tail can be negative, because it is possible to insert before the start of
     * the array, and then to delete from the end (the tail) of the array.
     */
    protected/private Int tail;

    /**
     * The index of the next element to remove from the `head`. The index starts at zero, and is
     * never reset, so the index into the `contents` array is the modulo of the `head` and the size
     * of the `contents` array. For any value of `tail`, `head<=tail`. When `head==tail`, the
     * CircularArray is empty.
     *
     * In theory, the head can be negative, because it is possible to insert before the start of
     * the array.
     */
    protected/private Int head;

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
        for (Int i = head; i < tail; ++i)
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
     * Given an absolute index such as the head and tail indexes, determine the corresponding index
     * in the contents array.
     *
     * @param index an index such as `head` or `tail`
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
        validateIndex(index);
        return contents[indexFor(head+index)].as(Element);
        }

    @Override
    @Op("[]=")
    void setElement(Int index, Element value)
        {
        validateIndex(index);
        contents[indexFor(head+index)] = value;
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
            contents[indexFor(--head)] = value;
            }
        else
            {
            validateIndex(index);
            Int mask = contents.size - 1;
            if (index < (size >>> 1))
                {
                // move the head
                for (Int iCopy = head, Int iLast = head + index - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-1 & mask] = contents[iCopy & mask];
                    }
                --head;
                }
            else
                {
                // move the tail
                for (Int iCopy = tail - 1, Int iLast = head + index; iCopy >= iLast; --iCopy)
                    {
                    contents[iCopy+1 & mask] = contents[iCopy & mask];
                    }
                ++tail;
                }
            contents[head+index & mask] = value;
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
            Int insertAt = head - additional;
            for (Element value : values)
                {
                contents[insertAt++ & mask] = value;
                }
            assert insertAt == head;
            head -= additional;
            }
        else
            {
            validateIndex(index);
            if (index < (size >>> 1))
                {
                // move the head
                for (Int iCopy = head, Int iLast = head + index - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-additional & mask] = contents[iCopy & mask];
                    }
                head -= additional;
                }
            else
                {
                // move the tail
                for (Int iCopy = tail - 1, Int iLast = head + index; iCopy >= iLast; --iCopy)
                    {
                    contents[iCopy+additional & mask] = contents[iCopy & mask];
                    }
                tail += additional;
                }

            Int insertAt = head + index;
            for (Element value : values)
                {
                contents[insertAt++ & mask] = value;
                }
            assert insertAt == head + index + additional;
            }

        return this;
        }

    @Override
    CircularArray delete(Int index)
        {
        validateIndex(index);
        if (index == 0)
            {
            contents[indexFor(head++)] = Null;
            }
        else if (index == size - 1)
            {
            contents[indexFor(--tail)] = Null;
            }
        else
            {
            Int mask = contents.size - 1;
            if (index < (size >>> 1))
                {
                // move the head forward
                for (Int iCopy = head + index - 1; iCopy >= head; --iCopy)
                    {
                    contents[iCopy+1 & mask] = contents[iCopy & mask];
                    }
                contents[head++ & mask] = Null;
                }
            else
                {
                // move the tail backwards
                for (Int iCopy = head + index + 1, Int iLast = tail - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-1 & mask] = contents[iCopy & mask];
                    }
                contents[--tail & mask] = Null;
                }
            }

        return this;
        }

    @Override
    CircularArray deleteAll(Interval<Int> interval)
        {
        validateIndex(interval.lowerBound);
        validateIndex(interval.upperBound);

        Int removing = interval.size;
        if (removing == size)
            {
            clear();
            return this;
            }
        else if (removing == 1)
            {
            return delete(interval.lowerBound);
            }

        Int     mask = contents.size - 1;
        Boolean fromTail;
        if (interval.lowerBound == 0)
            {
            fromTail = True;
            }
        else if (interval.upperBound == size-1)
            {
            fromTail = False;
            }
        else
            {
            fromTail = interval.lowerBound < (size - removing >>> 1);
            if (fromTail)
                {
                // move the head forward
                for (Int iCopy = head + interval.lowerBound - 1; iCopy >= head; --iCopy)
                    {
                    contents[iCopy+removing & mask] = contents[iCopy & mask];
                    }
                }
            else
                {
                // move the tail backwards
                for (Int iCopy = head + interval.upperBound + 1, Int iLast = tail - 1; iCopy <= iLast; ++iCopy)
                    {
                    contents[iCopy-1 & mask] = contents[iCopy & mask];
                    }
                }
            }

        if (fromTail)
            {
            while (removing-- > 0)
                {
                contents[head++ & mask] == Null;
                }
            }
        else
            {
            while (removing-- > 0)
                {
                contents[--tail & mask] == Null;
                }
            }

        return this;
        }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    @RO Int size.get()
        {
        return tail - head;
        }

    @Override
    @RO Boolean empty.get()
        {
        return tail == head;
        }

    @Override
    Iterator<Element> iterator()
        {
        return new Iterator()
            {
            Int prevHead = head;
            Int prevTail = tail;
            Int index    = head;

            @Override
            conditional Element next()
                {
                // adjust for a single deletion
                if (prevHead != head || prevTail != tail)
                    {
                    if (prevHead == head && prevTail-1 == tail)
                        {
                        prevTail = tail;
                        }
                    else if (prevHead+1 == head && prevTail == tail && index >= head)
                        {
                        prevHead = head;
                        }
                    else
                        {
                        throw new ConcurrentModification();
                        }
                    }

                if (index < tail)
                    {
                    return True, this.CircularArray[index++ - head];
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
        contents[indexFor(tail++)] = value;
        return this;
        }

    @Override
    @Op("+")
    CircularArray addAll(Iterable<Element> values)
        {
        ensureCapacity(values.size);
        for (Element value : values)
            {
            contents[indexFor(tail++)] = value;
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
            contents = new Element?[minCapacityFor(0)];
            }

        head = 0;
        tail = 0;

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