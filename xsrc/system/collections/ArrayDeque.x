/**
 * A Deque is a "double ended queue" (a "deque" or "dequeue"), which is a queue-like data structure
 * that is optimized for both insertion and deletion, and from both the "head" and the "tail" of the
 * queue.
 *
 * This implementation is a circular list on top of an array. An array is selected as an underlying
 * data structure for both its space efficiency and its random access and update efficiency, O(1).
 * Assuming that the dequeue is used in a manner similar to a queue, initially the "head" of the
 * structure is at index `0` in the array, and the "tail" of the structure advances with each
 * append to the queue. As items are removed in FIFO order, the tail advances towards the head. When
 * the head reaches the end of the array, and if the tail is no longer at the start of the array,
 * then the head "wraps around" to the front of the array, seemingly chasing the tail. Similarly,
 * when the tail reaches the end of the array, it too "wraps around" to the front of the array, once
 * again appearing to chase the head. At the point when the head catches up to the tail, the array
 * size must be increased.
 */
class ArrayDeque<ElementType>
        implements List<ElementType>
        implements Appender<ElementType>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an ArrayDeque with an initial and maximum capacity.
     *
     * @param initialCapacity  the number of elements to initially allocate storage for
     * @param maxCapacity      the maximum number of elements to allow storage for
     */
    construct(Int initialCapacity = 0, Int maxCapacity = Int.maxvalue)
        {
        // calculate the smallest power of 2 greater than the specified initial capacity
        contents = new ElementType?[minCapacityFor(initialCapacity)];

        assert maxCapacity > 0;
        this.maxCapacity = maxCapacity;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * An array that holds the elements of the ArrayDeque
     */
    protected/private ElementType?[] contents.set(ElementType?[] array)
        {
        assert array.size.bitCount == 1;
        super(array);
        }

    /**
     * The index of the next element to add at the `head`. The index starts at zero, and is never
     * reset, so the index into the `contents` array is the modulo of the `head` and the size of
     * the `contents` array. The size of the ArrayDeque is `head-tail`.
     *
     * In theory, the head can be negative, because it is possible to insert before the start of
     * the array, and then to delete from the end (the head) of the array.
     */
    protected/private Int head;

    /**
     * The index of the next element to remove from the `tail`. The index starts at zero, and is
     * never reset, so the index into the `contents` array is the modulo of the `tail` and the size
     * of the `contents` array. For any value of `head`, `tail<=head`. When `tail==head`, the
     * ArrayDeque is empty.
     *
     * In theory, the tail can be negative, because it is possible to insert before the start of
     * the array.
     */
    protected/private Int tail;

    /**
     * Reallocate the internal storage of the ArrayDeque.
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
        for (Int i : tail..head)
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
        return (elements * 2 - 1).leftmostBit.maxOf(16);
        }

    /**
     * Given
     *
     * @param counter
     *
     * @return
     */
    protected Int indexFor(Int index)
        {
        // contents must be of a power-of-two size
        return index & contents.size-1;
        }


    // ----- double-ended queue interface ----------------------------------------------------------

    /**
     * The allocated storage capacity of the ArrayDeque.
     */
    Int capacity.get()
        {
        return contents.size;
        }

    /**
     * If there is unused capacity in the ArrayDeque, then release as much of that excess capacity
     * as possible.
     *
     * @return the ArrayDeque
     */
    ArrayDeque trimCapacity()
        {
        Int oldCap = capacity;
        Int newCap = minCapacityFor(size);
        if (newCap < oldCap)
            {
            adjustCapacity(newCap);
            }

        return this;
        }

    /**
     * The potential maximum storage capacity of the ArrayDeque. Attempt to grow beyond this size
     * will result in an exception.
     */
    public/private Int maxCapacity;

    /**
     * The Queue that takes from the beginning of the list.
     */
    @Lazy Queue<ElementType> fifoQueue.calc()
        {
        TODO
        }

    /**
     * The Queue that takes from the end of the list.
     */
    @Lazy Queue<ElementType> lifoQueue.calc()
        {
        TODO
        }

    /**
     * An appender that appends in reverse order to the **start** of the list, instead of the end.
     */
    @Lazy Appender<ElementType> prepender.calc()
        {
        // TODO GG
//        return new Appender()
//            {
//            @Override
//            Appender add(ElementType v)
//                {
//                return ArrayDeque.this.insert(0, v);
//                }
//
//            @Override
//            Appender ensureCapacity(Int count)
//                {
//                return ArrayDeque.this.ensureCapacity(count);
//                }
//            };

        class Prepender
                implements Appender<ElementType>
            {
            @Override
            Prepender add(ElementType v)
                {
                ArrayDeque.this.insert(0, v);
                return this;
                }

            @Override
            Prepender ensureCapacity(Int count)
                {
                ArrayDeque.this.ensureCapacity(count);
                return this;
                }
            }

        return new Prepender();
        }


    // ----- List interface ------------------------------------------------------------------------

    @Override
    ArrayDeque insert(Int index, ElementType value)
        {
        TODO
        }

    @Override
    ArrayDeque insertAll(Int index, Iterable<ElementType> values)
        {
        TODO
        }

    @Override
    ArrayDeque delete(Int index)
        {
        TODO
        }

    @Override
    ArrayDeque delete(Range<Int> range)
        {
        TODO
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
        TODO
        }

    @Override
    @Op("+")
    ArrayDeque add(ElementType value)
        {
        TODO
        }

    @Override
    @Op("+")
    ArrayDeque addAll(Iterable<ElementType> values)
        {
        TODO
        }

    @Override
    @Op("-")
    ArrayDeque remove(ElementType value)
        {
        TODO
        }

    @Override
    ArrayDeque clear()
        {
        TODO
        }

    @Override
    ArrayDeque clone()
        {
        TODO
        }


    // ----- Appender interface ------------------------------------------------------------------

    @Override
    ArrayDeque add(Iterable<ElementType> iterable)
        {
        return addAll(iterable);
        }

    @Override
    ArrayDeque ensureCapacity(Int count)
        {
        Int newSize = size + count;
        if (newSize > capacity)
            {
            if (newSize > maxCapacity)
                {
                throw new SizeLimited("max=" + maxCapacity + ", requested=" + newSize);
                }

            // grow the capacity
            adjustCapacity(minCapacityFor(newSize));
            }

        return this;
        }
    }