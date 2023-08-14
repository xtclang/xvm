package org.xvm.util.concurrent;


import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicReference;

import java.util.concurrent.locks.LockSupport;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import java.util.stream.Stream;


/**
 * A lock-free {@link BlockingQueue} implementation which delegates to another thread-safe
 * {@link Queue} for element storage.
 *
 * @param <E> the element type
 * @author mf
 */
public class BlockingQueueAdapter<E> implements BlockingQueue<E>
    {
    /**
     * Construct a {@link BlockingQueueAdapter} which transforms the supplied thread-safe
     * {@link Queue} into a {@link BlockingQueue}.
     *
     * @param delegate the queue to delegate to for storage.
     */
    public BlockingQueueAdapter(Queue<E> delegate)
        {
        this.delegate = Objects.requireNonNull(delegate, "null delegate");
        }

    @Override
    public E remove()
        {
        return delegate.remove();
        }

    @Override
    public E poll()
        {
        return delegate.poll();
        }

    @Override
    public E element()
        {
        return delegate.element();
        }

    @Override
    public E peek()
        {
        return delegate.peek();
        }

    @Override
    public boolean remove(Object o)
        {
        return delegate.remove(o);
        }

    @Override
    public boolean containsAll(Collection<?> c)
        {
        return delegate.containsAll(c);
        }

    @Override
    public boolean removeAll(Collection<?> c)
        {
        return delegate.removeAll(c);
        }

    @Override
    public boolean removeIf(Predicate<? super E> filter)
        {
        return delegate.removeIf(filter);
        }

    @Override
    public boolean retainAll(Collection<?> c)
        {
        return delegate.retainAll(c);
        }

    @Override
    public void clear()
        {
        delegate.clear();
        }

    @Override
    public Spliterator<E> spliterator()
        {
        return delegate.spliterator();
        }

    @Override
    public Stream<E> stream()
        {
        return delegate.stream();
        }

    @Override
    public Stream<E> parallelStream()
        {
        return delegate.parallelStream();
        }

    @Override
    public int size()
        {
        return delegate.size();
        }

    @Override
    public boolean isEmpty()
        {
        return delegate.isEmpty();
        }

    @Override
    public boolean contains(Object o)
        {
        return delegate.contains(o);
        }

    @Override
    public Iterator<E> iterator()
        {
        return delegate.iterator();
        }

    @Override
    public void forEach(Consumer<? super E> action)
        {
        delegate.forEach(action);
        }

    @Override
    public Object[] toArray()
        {
        return delegate.toArray();
        }

    @Override
    public <T> T[] toArray(T[] a)
        {
        return delegate.toArray(a);
        }

    @Override
    public <T> T[] toArray(IntFunction<T[]> generator)
        {
        return delegate.toArray(generator);
        }


    // ----- BlockingQueue methods -----------------------------------------------------------------

    @Override
    public int remainingCapacity()
        {
        return Integer.MAX_VALUE;
        }

    @Override
    public int drainTo(Collection<? super E> c)
        {
        return drainTo(c, Integer.MAX_VALUE);
        }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements)
        {
        Objects.requireNonNull(c, "null target");
        int i = 0;
        for (; i < maxElements; ++i)
            {
            E value = poll();
            if (value == null)
                {
                break;
                }

            c.add(value);
            }

        return i;
        }

    // blocking aware methods

    @Override
    public void put(E value) throws InterruptedException
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public boolean offer(E value, long timeout, TimeUnit unit)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public boolean add(E value)
        {
        return offer(value);
        }

    @Override
    public boolean offer(E value)
        {
        if (delegate.offer(Objects.requireNonNull(value, "null value")))
            {
            signal();
            return true;
            }

        return false;
        }

    @Override
    public boolean addAll(Collection<? extends E> c)
        {
        boolean result = false;
        for (E value : c)
            {
            result |= add(value);
            }

        return result;
        }

    @Override
    @SuppressWarnings("all")
    public E take() throws InterruptedException
        {
        // poll(max) will never return null
        return poll(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException
        {
        E value = poll();
        return value == null ? pollInternal(timeout, unit) : value;
        }

    /**
     * Internal version of {@link #poll(long, TimeUnit)}.
     *
     * @param timeout the timeout
     * @param unit    the unit of the timeout
     *
     * @return the removed value or {@code null}
     *
     * @throws InterruptedException on interrupt
     */
    private E pollInternal(long timeout, TimeUnit unit) throws InterruptedException
        {
        Objects.requireNonNull(unit, "null unit");
        E                       value;
        Thread                  thread = Thread.currentThread();
        AtomicReference<Thread> parker = new AtomicReference<>(thread);

        awaiting.push(parker);

        long deadlineMs = timeout == Long.MAX_VALUE
                          ? Long.MAX_VALUE
                          : System.currentTimeMillis() + unit.toMillis(timeout);

        do
            {
            // before parking, we must double-check that a value hasn't been concurrently added
            value = poll();
            if (value == null)
                {
                if (timeout == Long.MAX_VALUE)
                    {
                    LockSupport.park(this);
                    }
                else
                    {
                    LockSupport.parkUntil(this, deadlineMs);
                    }

                // now that we've awoken we'll hopefully have a value
                value = poll();
                if (value == null)
                    {
                    if (Thread.interrupted())
                        {
                        unwait(thread, parker);
                        throw new InterruptedException();
                        }
                    else if (timeout != Long.MAX_VALUE && System.currentTimeMillis() >= deadlineMs)
                        {
                        unwait(thread, parker);
                        return null;
                        }
                    else if (parker.get() == null)
                        {
                        // we were signalled but didn't get a value; re-push/park
                        awaiting.push(parker = new AtomicReference<>(thread));
                        }
                    // else; no value and no signal; just re-park
                    }
                }
            }
        while (value == null);

        if (parker.get() == thread && !parker.compareAndSet(thread, null))
            {
            // we were signalled independent of getting a value; pass it along
            signal();
            }

        return value;
        }

    /**
     * Used to prematurely remove the calling thread from {@link #awaiting} when it exits without
     * having obtained a value.
     *
     * @param thread the calling thread
     * @param parker the calling thread's parking spot to remove
     */
    private void unwait(Thread thread, AtomicReference<Thread> parker)
        {
        if (parker.compareAndSet(thread, null))
            {
            // we've marked ourselves as not waiting, since we come here prematurely we also remove
            // ourselves from awaiting to avoid building up garbage
            awaiting.remove(parker);
            }
        else
            {
            // we were concurrently signalled and thus also removed from awaiting; but since we're
            // not taking a value (and were signaled) we need to pass that signal along to another
            // waiting thread
            signal();
            }
        }

    /**
     * Wake up one of the waiting consumer threads.
     */
    private void signal()
        {
        for (AtomicReference<Thread> parker = awaiting.poll();
             parker != null;
             parker = awaiting.poll())
            {
            Thread thread = parker.getAndSet(null);
            if (thread != null)
                {
                LockSupport.unpark(thread);
                return;
                }
            // else; it concurrently gave up; try the next parker
            }
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The delegate queue.
     */
    private final Queue<E> delegate;

    /**
     * "stack" of waiting threads.
     */
    private final ConcurrentLinkedDeque<AtomicReference<Thread>> awaiting =
            new ConcurrentLinkedDeque<>();
    }