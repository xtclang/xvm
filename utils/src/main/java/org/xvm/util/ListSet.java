package org.xvm.util;


import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * A simple implementation of a Set that maintains its contents in the order added, and allows
 * iterators to continue independently of other mutations on the Set. The underlying storage is a
 * circular array, and deletion from the middle of the sequence of elements is supported without
 * having to copy the remainder of the array. Null is a supported element value.
 */
public class ListSet<E>
        extends AbstractSet<E>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new ListSet.
     */
    public ListSet()
        {
        this(16);
        }

    /**
     * Construct a new ListSet of the specified initial capacity.
     *
     * @param cInitSize  the initial capacity; negative value indicates an immutable empty set
     */
    public ListSet(int cInitSize)
        {
        // make sure the size is a power of 2
        m_aElem = new Object[Integer.highestOneBit(Math.max(cInitSize, 4) * 2 - 1)];
        }

    /**
     * Construct a new ListSet that will contain the contents of the passed collection / list / set.
     *
     * @param that  a collection of compatible elements
     */
    public ListSet(Collection<? extends E> that)
        {
        this(that.size());
        addAll(that);
        }


    // ----- configuration -------------------------------------------------------------------------

    /**
     * Configure the Set to use only identity comparisons to determine element equality. This
     * disables the use of the {@link Object#equals} method on elements.
     */
    public ListSet<E> useIdentityEquality()
        {
        assert isEmpty();
        m_fSuppressEquals = true;
        return this;
        }

    /**
     * Configure the Set to avoid hashing for rapid  only identity comparisons to determine element equality. This
     * disables the use of the {@link Object#equals} method on elements.
     */
    public ListSet<E> disableHashIndex()
        {
        assert isEmpty();
        m_fSuppressHash = true;
        m_anHash        = null;
        return this;
        }

    /**
     * Configure the Set to avoid hashing for rapid  only identity comparisons to determine element equality. This
     * disables the use of the {@link Object#equals} method on elements.
     */
    public ListSet<E> disallowNulls()
        {
        assert isEmpty();
        m_fSuppressNull = true;
        return this;
        }


    // ----- ListSet methods -----------------------------------------------------------------------

    /**
     * @return the first item
     */
    public E first()
        {
        if (isEmpty())
            {
            throw new IllegalStateException();
            }

        Object[] aElem = m_aElem;
        int      nMask = aElem.length - 1;
        return toExternal(aElem[m_iTail % nMask]);
        }

    /**
     * @return the last added item
     */
    public E last()
        {
        if (isEmpty())
            {
            throw new IllegalStateException();
            }

        Object[] aElem = m_aElem;
        int      nMask = aElem.length - 1;
        return toExternal(aElem[(m_iHead - 1) % nMask]);
        }


    // ----- Set interface -------------------------------------------------------------------------

    @Override
    public int size()
        {
        return m_iHead - m_iTail - m_cBlank;
        }

    @Override
    public boolean contains(Object o)
        {
        return indexOf(toInternal(o)) >= 0;
        }

    @Override
    public boolean add(E e)
        {
        if (m_fSuppressNull && e == null)
            {
            throw new IllegalArgumentException("null value is not permitted");
            }

        Object  o    = toInternal(e);
        boolean fNew = indexOf(o) < 0;
        if (fNew)
            {
            addInternal(o);
            }

        return fNew;
        }

    @Override
    public boolean remove(Object o)
        {
        int i = indexOf(toInternal(o));
        if (i < 0)
            {
            return false;
            }

        remove(i);
        return true;
        }

    @Override
    public void clear()
        {
        if (m_iHead > m_iTail)
            {
            Arrays.fill(m_aElem, null);

            if (m_anHash != null)
                {
                Arrays.fill(m_anHash, -1);
                }

            m_iHead  = 0;
            m_iTail  = 0;
            m_cBlank = 0;

            // clearing all of the storage represents a re-organization of the underlying data
            ++m_cReorgs;
            }
        }

    @Override
    public Iterator<E> iterator()
        {
        return isEmpty()
                ? Collections.emptyIterator()
                : new SafeIterator();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Find the specified element.
     *
     * @param o  the element, in internal format
     *
     * @return the index of the element, tail <= i < head, or -1 if not found
     */
    private int indexOf(Object o)
        {
        // check if the set is empty
        int iHead = m_iHead;
        int iTail = m_iTail;
        if (iHead == iTail)
            {
            return -1;
            }

        // check the hash index
        if (indexEnabled())
            {
            return indexSearch(o);
            }

        // find the element in the set (using reference equality)
        Object[] aElem = m_aElem;
        int      nMask = aElem.length - 1;
        for (int i = iTail; i < iHead; ++i)
            {
            if (o == aElem[i & nMask])
                {
                return i;
                }
            }

        if (!m_fSuppressEquals)
            {
            // didn't find it by reference; try again, using equals()
            for (int i = iTail; i < iHead; ++i)
                {
                Object eCur = aElem[i & nMask];
                if (eCur != null && !(eCur instanceof Special) && eCur.equals(o))
                    {
                    return i;
                    }
                }
            }

        return -1;
        }

    /**
     * Convert an element of type E (including null) to the internal format.
     *
     * @param o  an element value (which may be null)
     *
     * @return an object in internal format that can be stored in the underlying storage
     */
    private static Object toInternal(Object o)
        {
        return o == null ? NULL : o;
        }

    /**
     * Convert an object of the internal format to an element of type E (including null).
     *
     * @param o  internal format object
     *
     * @return an element of type E (including null)
     */
    private static <E> E toExternal(Object o)
        {
        assert o != null && !(o instanceof Stop);
        return o == NULL ? null : (E) o;
        }

    /**
     * Add an object to the end of the set.
     *
     * @param o  the Object to add (which may even be a NULL or a HardStop)
     */
    private void addInternal(Object o)
        {
        // test if resize is necessary
        if (m_iHead - m_iTail >= m_aElem.length)
            {
            ensureSpace();
            }

        Object[] aElem = m_aElem;
        int      nMask = aElem.length - 1;
        int      iElem = m_iHead;
        if (iElem > m_iTail)
            {
            Object oPrev = m_aElem[iElem-1 & nMask];
            if (oPrev instanceof Stop && ((Stop) oPrev).isDisposable())
                {
                --iElem;
                --m_cBlank;
                }
            }

        // add the element
        aElem[iElem & nMask] = o;
        m_iHead = iElem+1;

        // update the index
        if (!m_fSuppressHash && !(o instanceof Stop))
            {
            if (indexEnabled())
                {
                indexAdd(o.hashCode(), iElem);
                }
            else if (size() >= INDEX_MIN)
                {
                // the set is now large enough to start indexing at this point
                indexInit();
                }
            }
        }

    /**
     * If the last element is a hard stop, then return it, otherwise add a hard stop and return it.
     *
     * @return the hard-stop for a new Iterator to use
     */
    private Stop ensureStop()
        {
        if (m_iHead > m_iTail)
            {
            Object last = m_aElem[m_iHead-1 & m_aElem.length-1];
            if (last instanceof Stop)
                {
                return (Stop) last;
                }
            }

        Stop stop = new Stop(++m_cStops);
        addInternal(stop);
        ++m_cBlank; // the invisible hard stop is treated as a blank inside the storage
        return stop;
        }

    /**
     * Make sure that the ListSet has space to append an element.
     */
    private void ensureSpace()
        {
        // if more than 1/8 of the elements are blank, then compact instead of growing the array
        if (m_cBlank > m_aElem.length >>> 3)
            {
            int cPrev = m_cBlank;
            compact();

            // verify that compaction release some substantial number of blanks, because if it
            // didn't, we'll grow the underlying storage immediately (to avoid repeated compactions,
            // each with little effect)
            if ((cPrev - m_cBlank) > Math.min(1024, m_aElem.length >>> 4))
                {
                return;
                }
            }

        grow();
        }

    /**
     * Remove blank spots from the middle of the ListSet's underlying storage.
     */
    private void compact()
        {
        Object[] aElem    = m_aElem;
        int      cElem    = aElem.length;
        int      nMask    = cElem - 1;
        boolean  fFront   = true;
        Stop     stop     = null;
        int      iSrc     = m_iTail;
        int      iSrcEnd  = m_iHead;
        int      iDest    = iSrc;
        int      iDestEnd = iSrcEnd - m_cBlank;
        int      cBlanks  = 0;
        boolean  fAdjust  = indexEnabled();
        while (iSrc < iSrcEnd)
            {
            Object elem = aElem[iSrc & nMask];
            if (elem instanceof Stop)
                {
                Stop stopCur = (Stop) elem;
                if (!fFront && !stopCur.isDisposable())
                    {
                    if (stop == null)
                        {
                        stop = stopCur;
                        }
                    else
                        {
                        stopCur.mergeInto(stop);
                        }
                    }
                }
            else if (elem != null)
                {
                fFront = false;
                if (iSrc >= iDestEnd)
                    {
                    aElem[iSrc & nMask] = null;
                    }

                // if we've been dragging a hard-stop along, this is where it gets inserted
                if (stop != null)
                    {
                    aElem[iDest++ & nMask] = stop;
                    ++cBlanks;
                    stop = null;
                    }

                if (fAdjust)
                    {
                    indexAdjust(elem.hashCode(), iSrc, iDest);
                    }
                aElem[iDest++ & nMask] = elem;
                }

            ++iSrc;
            }

        // if there is a trailing stop, then append it
        if (stop != null)
            {
            aElem[iDest++ & nMask] = stop;
            ++cBlanks;
            }

        // store the updated head pointer, and reduce the magnitude of the head & tail pointers
        int iTailOld = m_iTail;
        int iTailNew = iTailOld & nMask;
        int cDelta   = iTailOld - iTailNew;
        m_iHead  = iDest - cDelta;
        m_iTail  = iTailNew;
        m_cBlank = cBlanks;
        if (fAdjust)
            {
            indexAdjustAll(cDelta);
            }

        // compaction represents a re-organization of the underlying data
        ++m_cReorgs;
        }

    /**
     * Increase the size of the ListSet's underlying storage.
     */
    private void grow()
        {
        Object[] aOld     = m_aElem;
        int      cOld     = aOld.length;
        int      cNew     = cOld + cOld;

        // it's possible to grow too much; this implementation uses an index that must be larger
        // than the underlying storage; this also conveniently avoids the concern of a signed number
        assert cNew <= 0x20000000; // 500m

        Object[] aNew     = new Object[cNew];
        int      nOldMask = cOld - 1;
        int      nNewMask = cNew - 1;

        for (int i = m_iTail, c = m_iHead; i < c; ++i)
            {
            aNew[i & nNewMask] = aOld[i & nOldMask];
            }

        // store off the new element storage
        m_aElem = aNew;

        // grow the index as well
        if (indexEnabled())
            {
            indexInit();
            }

        // growing the storage represents a re-organization of the underlying data
        ++m_cReorgs;
        }

    /**
     * Verify that the specified index contains the specified element. If the element moved, then
     * find it and return its new index.
     *
     * @param i     an index (if known), otherwise pass -1
     * @param elem  the element to find by reference (not using equals method)
     *
     * @return the index where the element is found, otherwise -1
     */
    private int verify(int i, Object elem)
        {
        // first check optimistically if the passed-in-index is still correct
        final Object[] aElem = m_aElem;
        final int      nMask = aElem.length - 1;
        if (i >= m_iTail && i < m_iHead)
            {
            Object eVerify = aElem[i & nMask];
            if (elem == eVerify)
                {
                return i;
                }
            }

        if (indexEnabled())
            {
            return indexSearch(elem);
            }

        // scan the entire set for the element
        for (int iTest = m_iTail, iLast = m_iHead - 1; iTest <= iLast; ++iTest)
            {
            if (aElem[iTest & nMask] == elem)
                {
                return iTest;
                }
            }

        return -1;
        }

    /**
     * Find the specified "next element" and the hard-stop corresponding to the specified stop-id.
     *
     * @param eNext  the element to search for
     * @param nStop  the stop id to search for
     *
     * @return the element index, or -1 if either the element or the hard-stop could not be found or
     *         if the element was not located before the hard stop
     */
    private int verifyIterator(Object eNext, int nStop)
        {
        final Object[] aElem = m_aElem;
        final int nMask = aElem.length - 1;
        int iNext = -1;
        if (indexEnabled())
            {
            iNext = indexSearch(eNext);
            if (iNext < 0)
                {
                return -1;
                }

            for (int iTest = iNext+1, iLast = m_iHead - 1; iTest <= iLast; ++iTest)
                {
                Object eCur = aElem[iTest & nMask];
                if (eCur instanceof Stop)
                    {
                    Stop stop = (Stop) eCur;
                    if (stop.appliesTo(nStop))
                        {
                        return iNext;
                        }

                    // check if this stop is past the region where we might find the desired stop
                    if (stop.id() > nStop)
                        {
                        return -1;
                        }
                    }
                }
            }
        else
            {
            for (int iTest = m_iTail, iLast = m_iHead - 1; iTest <= iLast; ++iTest)
                {
                Object eCur = aElem[iTest & nMask];
                if (eCur == eNext)
                    {
                    assert iNext < 0;
                    iNext = iTest;
                    }
                else if (eCur instanceof Stop && ((Stop) eCur).appliesTo(nStop))
                    {
                    return iNext;
                    }
                }
            }

        return -1;
        }

    /**
     * Remove the element at the specified index.
     *
     * @param i  an index, {@code (m_iTail <= i < m_iHead)}
     */
    private void remove(int i)
        {
        assert i >= m_iTail && i < m_iHead;

        int    nMask = m_aElem.length-1;
        Object o     = m_aElem[i & nMask];
        assert o != null;

        // clear out the element
        m_aElem[i & nMask] = null;

        // update the index
        if (indexEnabled())
            {
            indexRemove(o.hashCode(), i);
            }

        if (i == m_iTail)
            {
            // advance the tail over the blank we just put in
            ++m_iTail;
            }
        else
            {
            ++m_cBlank;
            }
        }

    /**
     * @return true iff the ListSet is currently using indexes
     */
    private boolean indexEnabled()
        {
        return m_anHash != null;
        }

    /**
     * Make sure that the index for the ListSet exists, is configured correctly for the size of the
     * ListSet, and its contents match the contents of the ListSet.
     */
    private void indexInit()
        {
        int[] anHashOld   = m_anHash;

        int   cBucketsOld = anHashOld == null ? 0 : anHashOld.length;
        int   nModuloOld  = cBucketsOld >>> 1;
        int   nModuloNew = INDEX_SIZE[Integer.numberOfTrailingZeros(m_aElem.length)];
        assert nModuloNew > 0;
        if (nModuloNew == nModuloOld)
            {
            return;
            }

        // allocate the new index structure; size 2*(prime bigger than array size) and fill with -1
        int   cBucketsNew = nModuloNew << 1;
        int[] anHashNew   = new int[cBucketsNew];
        Arrays.fill(anHashNew, -1);
        m_anHash = anHashNew;

        if (cBucketsOld == 0)
            {
            // create the initial entries in the new index structure
            Object[] aElem = m_aElem;
            int      nMask = aElem.length-1;
            for (int i = m_iTail, c = m_iHead; i < c; ++i)
                {
                Object o = aElem[i & nMask];
                if (o != null && !(o instanceof Stop))
                    {
                    indexAdd(o.hashCode(), i);
                    }
                }
            }
        else
            {
            // copy the old index entries to the new index structure
            for (int iOld = 0, cOld = anHashOld.length; iOld < cOld; iOld += 2)
                {
                int iElem = anHashOld[iOld];
                if (iElem >= 0)
                    {
                    int nHash = anHashOld[iOld+1];
                    int iNew  = ((int) ((nHash & 0xFFFFFFFFFL) % nModuloNew)) << 1;
                    while (true)
                        {
                        // use the first empty bucket that we encounter
                        if (anHashNew[iNew] < 0)
                            {
                            anHashNew[iNew  ] = iElem;
                            anHashNew[iNew+1] = nHash;
                            break;
                            }

                        // proceed to the "next bucket" (technically, a pair of values)
                        iNew += 2;
                        if (iNew >= cBucketsNew)
                            {
                            iNew = 0;
                            }
                        }
                    }
                }
            }
        }

    /**
     * Add the specified hash entry.
     *
     * @param nHash  the hash code of the value
     * @param index  the index into the underlying ListSet storage for the value
     */
    private void indexAdd(int nHash, int index)
        {
        int[] anIndex  = m_anHash;
        int   cBuckets = anIndex.length;
        int   nModulo  = cBuckets >>> 1;
        int   iBucket  = ((int) ((nHash & 0xFFFFFFFFFL) % nModulo)) << 1;
        while (true)
            {
            // use the first empty bucket that we encounter
            int iElem = anIndex[iBucket];
            if (iElem < 0)
                {
                anIndex[iBucket  ] = index;
                anIndex[iBucket+1] = nHash;
                return;
                }

            // proceed to the "next bucket" (technically, a pair of values)
            iBucket += 2;
            if (iBucket >= cBuckets)
                {
                iBucket = 0;
                }
            }
        }

    /**
     * Remove the specified hash entry.
     *
     * @param nHash  the hash code of the value
     * @param index  the index into the underlying ListSet storage for the value
     */
    private void indexRemove(int nHash, int index)
        {
        int[]    anIndex  = m_anHash;
        int      cBuckets = anIndex.length;
        int      nModulo  = cBuckets >>> 1;
        int      iBucket  = ((int) ((nHash & 0xFFFFFFFFFL) % nModulo)) << 1;
        while (true)
            {
            // check if this is the bucket that we're looking for (first value is an index into the
            // underlying ListSet storage, second value is the hashcode for the element stored at
            // that index)
            int iElem = anIndex[iBucket];
            assert iElem >= 0;
            if (iElem == index)
                {
                assert anIndex[iBucket+1] == nHash;
                anIndex[iBucket] = -1;
                break;
                }

            // proceed to the "next bucket" (technically, a pair of values)
            iBucket += 2;
            if (iBucket >= cBuckets)
                {
                iBucket = 0;
                }
            }

        // any collisions would have "linearly probed" beyond this point, so shuffle them back
        // accordingly
        int     iBucketDest = iBucket;
        int     iBucketSrc  = iBucket;
        boolean fStraddled  = false;

        // if we wrap around the end of the hash index array, we need to remember the size of the
        // array as an adjustment (because we're not really at index 0, but at index arraysize+0).
        while (true)
            {
            iBucketSrc += 2;
            if (iBucketSrc >= cBuckets)
                {
                assert !fStraddled;
                iBucketSrc = 0;
                fStraddled = true;
                }

            int iElem = anIndex[iBucketSrc];
            if (iElem < 0)
                {
                return;
                }

            nHash   = anIndex[iBucketSrc+1];
            iBucket = ((int) ((nHash & 0xFFFFFFFFFL) % nModulo)) << 1;
            if (iBucket != iBucketSrc &&
                    (( fStraddled &&  iBucket > iBucketSrc && iBucket <= iBucketDest ) ||
                     (!fStraddled && (iBucket > iBucketSrc || iBucket <= iBucketDest))   ))
                {
                // we have to move the entry
                anIndex[iBucketDest  ] = iElem;
                anIndex[iBucketDest+1] = nHash;
                anIndex[iBucketSrc   ] = -1;

                // next place to deposit an index entry is where we just took one out of
                iBucketDest = iBucketSrc;
                fStraddled  = false;
                }
            }
        }

    /**
     * Use the index to find the specified object in the set.
     *
     * @param o  the object to search for in the set
     *
     * @return the index of the object, or -1 if the object is not in the set
     */
    private int indexSearch(Object o)
        {
        int[]    anIndex  = m_anHash;
        int      cBuckets = anIndex.length;
        int      nModulo  = cBuckets >>> 1;
        int      nHash    = o.hashCode();
        int      iBucket  = ((int) ((nHash & 0xFFFFFFFFFL) % nModulo)) << 1;
        Object[] aElem    = m_aElem;
        int      nMask    = aElem.length-1;
        while (true)
            {
            // check the bucket (first value is an index into the underlying ListSet storage, second
            // value is the hashcode for the element stored at that index)
            int iElem = anIndex[iBucket];
            if (iElem >= 0)
                {
                if (anIndex[iBucket+1] == nHash)
                    {
                    Object oElem = aElem[iElem & nMask];
                    if (o == oElem || !m_fSuppressEquals
                            && o != null && !(o instanceof Special) && oElem.equals(o))
                        {
                        return iElem;
                        }
                    }
                }
            else
                {
                return -1;
                }

            // proceed to the "next bucket" (technically, a pair of values)
            iBucket += 2;
            if (iBucket >= cBuckets)
                {
                iBucket = 0;
                }
            }
        }

    /**
     * Adjust the index for the specified hash value from the old specified index to the new
     * specified index.
     *
     * @param nHash     the hash value for the element that is being moved
     * @param indexOld  the old index of the value
     * @param indexNew  the new index of the value
     */
    private void indexAdjust(int nHash, int indexOld, int indexNew)
        {
        int[]    anIndex  = m_anHash;
        int      cBuckets = anIndex.length;
        int      nModulo  = cBuckets >>> 1;
        int      iBucket  = ((int) ((nHash & 0xFFFFFFFFFL) % nModulo)) << 1;
        while (true)
            {
            // check if this is the bucket that we're looking for (first value is an index into the
            // underlying ListSet storage, second value is the hashcode for the element stored at
            // that index)
            int iElem = anIndex[iBucket];
            assert iElem >= 0;
            if (iElem == indexOld)
                {
                assert anIndex[iBucket+1] == nHash;
                anIndex[iBucket] = indexNew;
                return;
                }

            // proceed to the "next bucket" (technically, a pair of values)
            iBucket += 2;
            if (iBucket >= cBuckets)
                {
                iBucket = 0;
                }
            }
        }

    /**
     * Adjust the pointers from the index data structure into the underlying ListSet storage when
     * the head & tail pointers are adjusted.
     *
     * @param indexDelta  the magnitude of the adjustment for all indexes
     */
    private void indexAdjustAll(int indexDelta)
        {
        int[] anIndex = m_anHash;
        for (int i = 0, c = anIndex.length; i < c; i += 2)
            {
            int index = anIndex[i];
            if (index >= 0)
                {
                anIndex[i] = index - indexDelta;
                }
            }
        }


    // ----- inner class: SafeIterator -------------------------------------------------------------

    /**
     * The SafeIterator is an iterator over the contents of the ListSet, but not including any
     * contents added after the iterator is created, and somewhat tolerant of other changes to the
     * ListSet as well.
     */
    private class SafeIterator
            implements Iterator<E>, AutoCloseable
        {
        public SafeIterator()
            {
            // obtain a hard-stop and notify it that there is now an additional iterator using it
            m_stop = ensureStop();
            m_stop.beginUse();

            // record the current state of the enclosing ListSet
            m_nExpect = m_cReorgs;

            // prime the iterator
            loadNext(m_iTail);
            }

        @Override
        public boolean hasNext()
            {
            return m_eNext != null && synced();
            }

        @Override
        public E next()
            {
            Object eCur = m_eNext;
            if (eCur == null || !synced())
                {
                throw new NoSuchElementException();
                }

            m_iPrev = m_iNext;
            m_ePrev = eCur;

            loadNext(m_iNext + 1);

            return toExternal(eCur);
            }

        @Override
        public void remove()
            {
            if (m_iPrev >= 0)
                {
                int iActual = verify(m_iPrev, m_ePrev);
                if (iActual >= 0)
                    {
                    ListSet.this.remove(m_iPrev);
                    m_iPrev = -1;
                    m_ePrev = null;
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        @Override
        public void close()
            {
            if (m_nExpect >= 0)
                {
                m_iNext   = -1;
                m_eNext   = null;
                m_nExpect = -1;

                // when the iterator is no longer active, it notifies its hard-stop (so that when
                // no other iterators are using that hard-stop, it can be discarded)
                m_stop.finishUse();

                // the previous element information is not destroyed, just in case someone still
                // calls remove() even after the iterator is exhausted
                }
            }

        @Override
        public String toString()
            {
            return "SafeIterator-" + m_stop.id();
            }

        /**
         * Load the next element to iterate.
         *
         * @param iNext  the index at which to start the search for the next element
         */
        private void loadNext(int iNext)
            {
            // by the time that we get here, synced() has already completed successfully
            Object[] aElem = m_aElem;
            int      nMask = aElem.length - 1;
            for (int iLast = m_iHead - 1; iNext <= iLast; ++iNext)
                {
                Object eNext = aElem[iNext & nMask];
                if (eNext instanceof Stop)
                    {
                    if (((Stop) eNext).appliesTo(m_stop.id()))
                        {
                        break;
                        }
                    }
                else if (eNext != null)
                    {
                    this.m_iNext = iNext;
                    this.m_eNext = eNext;
                    return;
                    }
                }

            close();
            }

        /**
         * If a significant change has occurred to the ListSet, then adjust this iterator to
         * compensate accordingly.
         *
         * @return true iff the iterator is in sync with the ListSet; false if a change has occurred
         *         to the ListSet such that the iterator cannot continue
         */
        private boolean synced()
            {
            if (m_nExpect != m_cReorgs)
                {
                assert m_nExpect >= 0;

                // a compaction or other change has occurred; figure out what changed; first, make
                // sure that the next item to iterate is still in the set, and make sure that the
                // hard stop for this iterator is still in the set (somewhere AFTER the element)
                m_iNext = verifyIterator(m_eNext, m_stop.id());
                if (m_iNext < 0)
                    {
                    // something significant changed in the ListSet (e.g. a clear) that causes the
                    // iterator to cancel
                    m_eNext = null;
                    return false;
                    }

                m_nExpect = m_cReorgs;
                }

            return true;
            }

        /**
         * The index of the previous element returned, or -1.
         */
        private int    m_iPrev = -1;

        /**
         * The previous element returned.
         */
        private Object m_ePrev;

        /**
         * The last-known index of the next element to return, or -1.
         */
        private int    m_iNext = -1;

        /**
         * The next element to return.
         */
        private Object m_eNext;

        /**
         * The hard-stop for the Iterator.
         */
        private Stop   m_stop;

        /**
         * The organizational id (counter) expected on the ListSet; any difference indicates that
         * the Iterator needs to re-sync with the ListSet. Set to -1 by the close() method.
         */
        private int    m_nExpect;
        }


    // ----- inner classes & constants -------------------------------------------------------------

    /**
     * A base class for any special objects stored in the set's underlying storage.
     */
    private static class Special {}

    /**
     * A representation of an Interator's planned "hard stop" stopping point that can be stored
     * (invisibly) in the set.
     */
    private static class Stop
            extends Special
        {
        /**
         * Construct a hard-stop for the specified id.
         *
         * @param nStop  a hard-stop id
         */
        Stop(int nStop)
            {
            first = last = nStop;
            }

        /**
         * @return an id for this hard-stop
         */
        int id()
            {
            return next == null
                    ? last
                    : next.id();
            }

        /**
         * Determine if this hard-stop is responsible for the specified hard-stop id.
         *
         * @param nStop  a hard-stop id
         *
         * @return true if the hard-stop id is represented by this hard-stop
         */
        boolean appliesTo(int nStop)
            {
            return next == null
                    ? nStop >= first && nStop <= last
                    : next.appliesTo(nStop);
            }

        /**
         * Mark the hard-stop as being used by one more iterator.
         */
        void beginUse()
            {
            if (next == null)
                {
                ++active;
                }
            else
                {
                next.beginUse();
                }
            }

        /**
         * Mark the hard-stop as being used by one less iterator.
         */
        void finishUse()
            {
            if (next == null)
                {
                --active;
                assert active >= 0;
                }
            else
                {
                next.finishUse();
                }
            }

        /**
         * Take all of the information from this hard-stop and combine it with information from
         * another hard-stop, so that the other hard-stop represents the combination of the two,
         * and then link to that other hard-stop so that future operations against this hard-stop
         * will be delegated to that hard-stop.
         *
         * @param that  the hard-stop to merge this hard-stop into
         */
        void mergeInto(Stop that)
            {
            that.first  = Math.min(this.first, that.first);
            that.last   = Math.max(this.last , that.last );
            that.active = this.active + that.active;

            this.first  = -1;
            this.last   = -1;
            this.active = 0;
            this.next   = that;
            }

        /**
         * @return true if it is safe to discard this hard-stop as part of ListSet compaction
         */
        boolean isDisposable()
            {
            return active <= 0;
            }

        @Override
        public String toString()
            {
            return "<stop:" + (first == last ? String.valueOf(last) : (first + "-" + last)) + '>';
            }

        /**
         * The first of the IDs that this hard-stop represents.
         */
        int  first;

        /**
         * The last of the IDs that this hard-stop represents.
         */
        int  last;

        /**
         * The number of iterators actively using this hard-stop.
         */
        int  active;

        /**
         * The hard-stop to delegate to, if this hard-stop has been merged into another hard-stop.
         */
        Stop next;
        }

    /**
     * A class whose purpose is to represent a "null" value in the set.
     */
    private static class Null
            extends Special
        {
        @Override
        public int hashCode()
            {
            return 2147483629;
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj == this || obj instanceof Null;
            }

        @Override
        public String toString()
            {
            return "<null>";
            }
        }

    /**
     * The singleton used to represent a "null" value that is stored in the set.
     */
    private static Object NULL = new Null();

    /**
     * Do not index small sets.
     */
    private static int   INDEX_MIN  = 12;

    /**
     * The amount of storage for an index, based on the size of the underlying storage for the
     * ListSet, which is always of a power-of-two size.
     */
    private static int[] INDEX_SIZE = new int[]
        {
               0,        0,         0,         0,        23,    // 1, 2, 4, 8, 16,
              47,       97,       191,       373,       757,    // 32, ...
            1543,     2999,      5987,     11987,     23993,    // 1024, ...
           47981,    95989,    189877,    389447,    779353,    // 32768, ...
         1499977,  2999999,   5999993,  11999989,  23999999,    // 2^20, ...
        47999969, 95999993, 191999987, 383999983, 767999993,    // 2^25, ...
        };


    // ----- data members --------------------------------------------------------------------------

    /**
     * The contents of the set. Must be a power of 2.
     */
    private Object[] m_aElem;

    /**
     * The "head" of the growing list is the index of the next element to add;
     * {@code head >= tail >= 0}. The value of this index cannot be used as an index into m_aElem
     * unless it is modulo'd by the length of the array.
     */
    private int m_iHead;

    /**
     * The "tail" is the index of the oldest element in the list. The value of this index cannot be
     * used as an index into m_aElem unless it is modulo'd by the length of the array.
     */
    private int m_iTail;

    /**
     * The count of blank (nulls i.e. removed, and hard-stops) elements in the middle of the list.
     */
    private int m_cBlank;

    /**
     * The number of times that the list has been cleared, resized, or compacted.
     */
    private int m_cReorgs;

    /**
     * The hard stop counter. Each hard-stop has an integer identity, which comes from this counter.
     */
    private int m_cStops;

    /**
     * A hash index, using open addressing with linear probing, composed of pairs of values, the
     * first of which is an index into the underlying ListSet storage (or -1 if the bucket is
     * unused), and the second of which is the hashcode for the element stored at that index.
     */
    private int[] m_anHash;

    /**
     * Set to true to indicate that only identity equality is used, and thus equals() should NOT be
     * used to compare elements.
     */
    private boolean m_fSuppressEquals;

    /**
     * Set to true to indicate that hashed lookups are not allowed.
     */
    private boolean m_fSuppressHash;

    /**
     * Set to true to indicate that null values are not allowed.
     */
    private boolean m_fSuppressNull;
    }
