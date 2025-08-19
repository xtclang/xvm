package org.xvm.util.concurrent;


import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * A lock-free {@link java.util.concurrent.BlockingQueue BlockingQueue} based on a
 * {@link java.util.concurrent.ConcurrentLinkedQueue ConcurrentLinkedQueue}.
 *
 * @param <E> the element type
 * @author mf
 */
public class ConcurrentLinkedBlockingQueue<E> extends BlockingQueueAdapter<E>
    {
    public ConcurrentLinkedBlockingQueue()
        {
        super(new ConcurrentLinkedQueue<>());
        }
    }