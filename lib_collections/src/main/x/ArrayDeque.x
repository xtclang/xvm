import ecstasy.collections.SizeLimited;

/**
 * A Deque is a "double ended queue" (a "deque" or "dequeue"), which is a queue-like data structure
 * that is optimized for both insertion and deletion, and from both the "head" and the "tail" of the
 * queue. This allows the implementation to support both prepending and appending, and both FIFO and
 * LIFO ordering.
 *
 * This implementation uses a CircularArray for its storage, and adds an optional size limit.
 *
 * The Appender interface provided by the ArrayDeque appends to the end of the array, and the Queue
 * interface provided by the ArrayDeque is the corresponding FIFO queue. To prepend to the queue, or
 * to use LIFO instead of FIFO, obtain the [lifoQueue].
 */
class ArrayDeque<Element>
        implements Queue<Element>
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
        array = new CircularArray(initialCapacity);

        assert maxCapacity > 0;
        this.maxCapacity = maxCapacity;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * A circular array that holds the elements of the ArrayDeque
     */
    protected/private CircularArray<Element | Consumer> array;

    /**
     * True iff the ArrayDeque is in piping mode, which means that it has pending requests for
     * piping incoming elements.
     */
    protected/private Boolean piping;

    /**
     * The piping destination for all incoming elements, if the ArrayDeque has been instructed to
     * `pipeAll()`. Note that there may still be pending piping requests from previous `pipeNext()`
     * calls; those pending requests must be filled first.
     */
    protected/private Consumer? drain;

    /**
     * Verify that there is no Consumer previously registered by a call to `pipeAll()`.
     *
     * @throws IllegalState  if the Queue has a drain (a consumer previously registered by
     *                       `pipeAll()`)
     *
     * @return True
     */
    protected Boolean verifyNoDrain()
        {
        if (drain != Null)
            {
            throw new IllegalState("Queue has a pipeAll() operation active");
            }

        return True;
        }

    /**
     * Determine if the next element to be added to the queue needs to be piped, and if so, what the
     * consumer is for that element.
     *
     * @return True iff the next element added to the queue will need to be piped
     * @return the Consumer to pipe to (conditional)
     */
    protected conditional Consumer pendingPipe()
        {
        if (piping)
            {
            // check for "pipeAll"
            if (array.empty)
                {
                return True, drain.as(Consumer);
                }

            // it's a "pipeNext"; see if this is the last pending piping operation
            if (array.size <= 1 && drain == Null)
                {
                piping = False;
                }
            Consumer pipe = array[0].as(Consumer);
            array.delete(0);
            return True, pipe;
            }

        return False;
        }

    protected Element takeFirst()
        {
        Element value = array[0].as(Element);
        array.delete(0);
        return value;
        }

    protected Element takeLast()
        {
        Element value = array[size-1].as(Element);
        array.delete(size-1);
        return value;
        }


    // ----- double-ended queue interface ----------------------------------------------------------

    /**
     * The number of items currently in the ArrayDeque.
     */
    Int size.get()
        {
        return piping ? 0 : array.size;
        }

    /**
     * True iff the ArrayDeque is empty.
     */
    Boolean empty.get()
        {
        return piping | array.empty;
        }

    /**
     * The allocated storage capacity of the ArrayDeque.
     */
    Int capacity.get()
        {
        return array.capacity;
        }

    /**
     * If there is unused capacity in the ArrayDeque, then release as much of that excess capacity
     * as possible.
     *
     * @return the ArrayDeque
     */
    ArrayDeque trimCapacity()
        {
        array.trimCapacity();
        return this;
        }

    /**
     * The potential maximum storage capacity of the ArrayDeque. Attempt to grow beyond this size
     * will result in an exception.
     */
    Int maxCapacity.set(Int max)
        {
        assert max >= 0;
        if (max < size)
            {
            throw new SizeLimited("size=" + size + ", requested-max=" + max+ ", old-max=" + maxCapacity);
            }
        super(max);
        }

    /**
     * A LIFO view of the dequeue; it adds to the head and takes from the tail.
     */
    @Lazy Queue<Element> reversed.calc()
        {
        return new LifoQueue();
        }


    // ----- Appender interface ------------------------------------------------------------------

    @Override
    ArrayDeque add(Element v)
        {
        if (Consumer consume := pendingPipe())
            {
            consume(v);
            }
        else
            {
            ensureCapacity(1);
            array.add(v);
            }

        return this;
        }

    @Override
    ArrayDeque addAll(Iterable<Element> iterable)
        {
        if (piping)
            {
            super(iterable);
            }
        else
            {
            ensureCapacity(iterable.size);
            array.addAll(iterable);
            }

        return this;
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

            array.ensureCapacity(count);
            }

        return this;
        }


    // ----- Queue interface -----------------------------------------------------------------------

    @Override
    conditional Element next()
        {
        if (empty)
            {
            verifyNoDrain();
            return False;
            }
        return True, takeFirst();
        }

    @Override
    Element take()
        {
        if (empty)
            {
            // since the queue is empty, we need to create a future result that will get fulfilled
            // when an element gets added to the queue; we use a similar mechanism to what happens
            // with a call to pipeNext() when the queue is empty, which is to add a Consumer to the
            // array that we will pipe the next element to, but in this case, the Consumer is our
            // own, and its purpose is to complete the future
            verifyNoDrain();
            piping = True;

            // when something shows up in the queue, we will fulfill the take()
            // we make a note of this by storing the fulfillment function into the queue itself
            @Future Element promisedElement;
            array.add(element -> {promisedElement = element;});

            // if (for whatever reason) the future doesn't survive (for example, as the result of
            // the future being closed, or for whatever other exceptional reason), then we'll erase
            // the note reminding us to fulfill the promise, because at that point it will no longer
            // be possible to fulfill the promise
            &promisedElement.whenComplete((v, e) ->
                {
                if (e != Null)
                    {
                    array.remove(promisedElement);
                    }
                });

            return promisedElement;
            }

        return takeFirst();
        }

    @Override
    Cancellable pipeNext(Consumer pipe)
        {
        if (empty)
            {
            verifyNoDrain();
            array.add(pipe);
            piping = True;
            return () ->
                {
                if (piping)
                    {
                    array.remove(pipe);
                    if (array.empty && drain == Null)
                        {
                        piping = False;
                        }
                    }
                };
            }

        pipe(takeFirst());
        return () -> {};
        }

    @Override
    Cancellable pipeAll(Consumer pipe)
        {
        verifyNoDrain();
        drain = pipe;

        // drain the queue
        while (!empty)
            {
            pipe(takeFirst());
            }
        trimCapacity();

        piping = True;
        return () ->
            {
            if (drain == pipe)
                {
                drain  = Null;
                piping = !array.empty;
                }
            };
        }


    // ----- LIFO Queue inner class ----------------------------------------------------------------

    /**
     * A LIFO queue is one that takes from the end and "appends" to the beginning of the underlying
     * array.
     */
    protected class LifoQueue
            implements Queue<Element>
        {
        @Override
        LifoQueue add(Element v)
            {
            if (Consumer consume := pendingPipe())
                {
                consume(v);
                }
            else
                {
                ensureCapacity(1);
                array.insert(0, v);
                }

            return this;
            }

        @Override
        LifoQueue ensureCapacity(Int count)
            {
            this.ArrayDeque.ensureCapacity(count);
            return this;
            }

        @Override
        conditional Element next()
            {
            if (empty)
                {
                verifyNoDrain();
                return False;
                }

            return True, takeLast();
            }

        @Override
        Element take()
            {
            if (empty)
                {
                // the take() implementation on the FIFO queue will create a future result
                return this.ArrayDeque.take();
                }

            return takeLast();
            }

        @Override
        Cancellable pipeNext(Consumer pipe)
            {
            if (empty)
                {
                verifyNoDrain();
                array.add(pipe);
                piping = True;
                return () ->
                    {
                    if (piping)
                        {
                        array.remove(pipe);
                        if (array.empty && drain == Null)
                            {
                            piping = False;
                            }
                        }
                    };
                }

            pipe(takeLast());
            return () -> {};
            }

        @Override
        Cancellable pipeAll(Consumer pipe)
            {
            verifyNoDrain();
            drain = pipe;

            // drain the queue
            while (!empty)
                {
                pipe(takeLast());
                }
            trimCapacity();

            piping = True;
            return () ->
                {
                if (drain == pipe)
                    {
                    drain  = Null;
                    piping = !array.empty;
                    }
                };
            }
        }
    }