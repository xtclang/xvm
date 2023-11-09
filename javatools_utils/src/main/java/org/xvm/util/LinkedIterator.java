package org.xvm.util;


import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;


/**
 * An Iterator over multiple Iterators.
 */
public class LinkedIterator<E>
        implements Iterator<E>
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct an Iterator that iterates over the contents of zero or more
     * Iterators.
     *
     * @param aIter  the Iterators containing the elements to iterate over
     */
    public LinkedIterator(Iterator<E>... aIter)
        {
        f_aIter = Objects.requireNonNullElse(aIter, new Iterator[0])
        }


    // ----- Iterator interface ------------------------------------------------

    @Override
    public boolean hasNext()
        {
        return m_nState == HAS_NEXT || prepareNextElement();
        }

    @Override
    public E next()
        {
        if (m_nState == HAS_NEXT || prepareNextElement())
            {
            final E eNext = m_iterCur.next();
            m_nState = CAN_REMOVE;
            return eNext;
            }

        throw new NoSuchElementException();
        }

    @Override
    public void remove()
        {
        if (m_nState == CAN_REMOVE)
            {
            m_iterCur.remove();
            m_nState = NOT_READY;
            }
        else
            {
            throw new IllegalStateException();
            }
        }


    // ----- internal ----------------------------------------------------------

    /**
     * Make sure that there is a current Iterator with at least one element
     * available.
     *
     * @return true iff a current Iterator could be prepared, or false iff all
     *         elements to iterate have been exhausted
     */
    private boolean prepareNextElement()
        {
        do
            {
            if (m_iterCur.hasNext())
                {
                m_nState = HAS_NEXT;
                return true;
                }

            m_nState = NOT_READY;
            if (!prepareNextIterator())
                {
                return false;
                }
            }
        while (true);
        }

    /**
     * Load the next Iterator to use.
     *
     * @return true iff a next Iterator exists
     */
    private boolean prepareNextIterator()
        {
        for (int i = 0, c = f_aIter.length; i < c; ++i)
            {
            Iterator<E> iter = f_aIter[i];
            if (iter != null)
                {
                m_iterCur = iter;
                m_nState  = NOT_READY;

                // remove the Iterator from the array of Iterators to iterate
                // over; also,  by clearing the references in the array to each
                // iterator as it is loaded, it allows each iterator to be
                // garbage collected after it is exhausted
                f_aIter[i] = null;

                return true;
                }
            }

        // by setting the current iterator to a reference to an empty iterator,
        // the reference to any previous iterator is not retained, allowing it
        // to be garbage collected
        m_iterCur = Collections.emptyIterator();

        return false;
        }


    // ----- data members ------------------------------------------------------

    /**
     * An array of Iterator references whose contents this LinkedIterator will
     * iterate over.
     */
    private final Iterator<E>[] f_aIter;

    /**
     * The current underlying Iterator whose contents are being iterated over.
     */
    private Iterator<E> m_iterCur = Collections.emptyIterator();

    /**
     * State: A current iterator has not yet been loaded, or the current
     * iterator that has been loaded has not yet been verified to contain an
     * element ready to iterate over. Also used for both the initial and the
     * terminal state of the LinkedIterator.
     */
    private static final int NOT_READY  = 0;
    /**
     * State: A current iterator is available, and the current iterator has been
     * verified to contain an element ready to iterate over.
     */
    private static final int HAS_NEXT   = 1;
    /**
     * State: An element was returned from the current iterator, and thus it is
     * valid to invoke {@link Iterator#remove()} against the same.
     */
    private static final int CAN_REMOVE = 2;

    /**
     * The current state of the LinkedIterator; one of {@link #NOT_READY},
     * {@link #HAS_NEXT}, or {@link #CAN_REMOVE}.
     */
    private int m_nState = NOT_READY;
    }