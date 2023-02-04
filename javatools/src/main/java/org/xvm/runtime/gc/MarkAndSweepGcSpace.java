package org.xvm.runtime.gc;


import java.util.HashSet;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * A simple {@code mark-and-sweep} style collector.
 * <p>
 * Objects are allocated on the java heap but tracked via the {@link GcSpace}.
 *
 * @author mf
 */
public class MarkAndSweepGcSpace<V>
        implements GcSpace
    {

    /**
     * Construct a {@link MarkAndSweepGcSpace}.
     *
     * @param accessor        the accessor of accessing the contents of an object
     * @param clearedListener a function to invoke with a pointer to a weak-ref once it's been cleared
     */
    public MarkAndSweepGcSpace(ObjectManager<V> accessor, LongConsumer clearedListener)
        {
        this(accessor, clearedListener, Long.MAX_VALUE, Long.MAX_VALUE);
        }

    /**
     * Construct a {@link MarkAndSweepGcSpace} with limits.
     *
     * @param accessor        the accessor of accessing the contents of an object
     * @param clearedListener a function to invoke with a pointer to a weak-ref once it's been cleared
     * @param cbLimitSoft     the byte size to try to stay within
     * @param cbLimitHard     the maximum allowable byte size
     */
    public MarkAndSweepGcSpace(ObjectManager<V> accessor,
                               LongConsumer clearedListener,
                               long cbLimitSoft,
                               long cbLimitHard)
        {
        f_accessor = accessor;
        f_clearedListener = clearedListener;
        f_cbLimitSoft = cbLimitSoft;
        f_cbLimitHard = cbLimitHard;

        for (int i = 0; i < m_anFreeSlots.length; ++i)
            {
            m_anFreeSlots[i] = i;
            }
        m_nTopFree = m_anFreeSlots.length - 1;
        }

    @Override
    public long allocate(int cFields, boolean fWeak)
            throws OutOfMemoryError
        {
        if (m_nTopFree < 0 || m_cBytes > f_cbLimitSoft)
            {
            gc();
            if (m_cBytes > f_cbLimitHard)
                {
                throw new OutOfMemoryError("hard limit exceeded");
                }
            else if (m_nTopFree < 0)
                {
                grow();
                }
            }

        V resource = f_accessor.allocate(cFields);
        getAndSetHeaderBit(resource, MARKER_MASK, m_fAllocationMarker);
        setFieldCount(resource, cFields);
        if (fWeak)
            {
            getAndSetHeaderBit(resource, WEAK_MASK, true);
            }
        m_cBytes += f_accessor.getByteSize(resource);

        int slot = m_anFreeSlots[m_nTopFree--];
        m_aObjects[slot] = resource;
        return address(slot);
        }

    @Override
    public boolean isValid(long address)
        {
        if (!isLocal(address))
            {
            return false;
            }

        int slot = slot(address);
        return slot < m_aObjects.length && m_aObjects[slot] != null;
        }

    /**
     * Tests if the address is valid and if no return the storage object.
     *
     * @param address the address of the object
     * @return the object
     * @throws SegFault if the address is invalid
     */
    private V ensure(long address)
        throws SegFault
        {
        if (address == NULL)
            {
            throw new NullPointerException();
            }
        else if (!isLocal(address))
            {
            throw new SegFault();
            }

        int slot = slot(address);
        if (slot > m_aObjects.length)
            {
            throw new SegFault();
            }

        V o = m_aObjects[slot];
        if (o == null)
            {
            throw new SegFault();
            }

        return o;
        }

    @Override
    public long getField(long address, int index) throws SegFault
        {
        return f_accessor.getField(ensure(address), index);
        }

    @Override
    public void setField(long address, int index, long handle) throws SegFault
        {
        f_accessor.setField(ensure(address), index, handle);
        }

    @Override
    public void addRoot(Supplier<? extends PrimitiveIterator.OfLong> root)
        {
        f_setRoots.add(root);
        }

    @Override
    public void removeRoot(Supplier<? extends PrimitiveIterator.OfLong> root)
        {
        f_setRoots.remove(root);
        }

    @Override
    public long getByteCount()
        {
        return m_cBytes;
        }

    @Override
    public void gc()
        {
        // mark
        boolean fReachableMarker = !m_fAllocationMarker;
        m_fAllocationMarker = fReachableMarker;
        int[] state = null;
        for (var root : f_setRoots)
            {
            for (var liter = root.get(); liter.hasNext(); )
                {
                state = walkAndMark(state, liter.next(), fReachableMarker, 0);
                }
            }

        // sweep
        V[] aObjects = m_aObjects;
        int[] anFreeSlots = m_anFreeSlots;
        int[] anNotify = null;
        int nNotifyTop = 0;
        for (int i = 0; i < aObjects.length; ++i)
            {
            V o = aObjects[i];
            if (o != null)
                {
                long header = f_accessor.getHeader(o);
                if (((header & MARKER_MASK) == MARKER_MASK) != fReachableMarker)
                    {
                    // "unmarked" object; free the space
                    aObjects[i] = null;
                    anFreeSlots[++m_nTopFree] = i;
                    m_cBytes -= f_accessor.getByteSize(o);
                    f_accessor.free(o);
                    }
                else if ((header & WEAK_MASK) == WEAK_MASK)
                    {
                    int[] anNotifyNew = handleWeakSweep(o, i, anNotify, nNotifyTop, fReachableMarker);
                    if (anNotifyNew != null && nNotifyTop < anNotifyNew.length && anNotifyNew[nNotifyTop] != 0)
                        {
                        anNotify = anNotifyNew;
                        ++nNotifyTop;
                        }
                    }
                }
            }

        if (anNotify != null)
            {
            for (int i = 0; i < nNotifyTop; ++i)
                {
                f_clearedListener.accept(address(anNotify[i]));
                }
            }
        }

    /**
     * Walk and mark the graph of objects reachable from a given object.
     *
     * @param state            reusable int[] tracking the walk state for iterative marking, or {@code null}
     * @param address          the source object address
     * @param fReachableMarker the value to mark the objects with
     * @param depth            the recursion depth
     * @return the current state or {@code null}
     */
    private int[] walkAndMark(int[] state, long address, boolean fReachableMarker, int depth)
        {
        // start with a fast stack recursion based walk, but dynamically switch to an iterative approach when the
        // stack gets too deep and we risk StackOverflow
        if (isLocal(address))
            {
            if (depth == 1024)
                {
                // deep stack switch to slower iterative scan
                state = walkAndMarkIterative(state, address, fReachableMarker);
                }
            else
                {
                V o = ensure(address);
                if (getAndSetHeaderBit(o, MARKER_MASK, fReachableMarker) != fReachableMarker)
                    {
                    boolean fWeak = getHeaderBit(o, WEAK_MASK);
                    for (int i = fWeak ? 1 : 0, c = getFieldCount(o); i < c; ++i)
                        {
                        state = walkAndMark(state, f_accessor.getField(o, i), fReachableMarker, depth + 1);
                        }
                    }
                }
            }

        return state;
        }

    /**
     * Walk and mark the graph of objects reachable from a given object.
     *
     * @param state            reusable int[] tracking the walk state for iterative marking, or {@code null}
     * @param address          the source object address
     * @param fReachableMarker the value to mark the objects with
     * @return the current state or {@code null}
     */
    private int[] walkAndMarkIterative(int[] state, long address, boolean fReachableMarker)
        {
        // state consists of pairs of truncated address (slots) its remaining field count to visit
        if (isLocal(address))
            {
            V o = ensure(address);
            if (getAndSetHeaderBit(o, MARKER_MASK, fReachableMarker) != fReachableMarker)
                {
                // initial push
                int iTop = -1;
                if (state == null)
                    {
                    state = new int[128];
                    }
                state[++iTop] = slot(address);
                state[++iTop] = getFieldCount(o);

                do
                    {
                    while (state[iTop] > 0) // remaining field count
                        {
                        // read our next field
                        o = ensure(address(state[iTop - 1]));

                        int iField = --state[iTop];
                        if (iField == 0 && getHeaderBit(o, WEAK_MASK))
                            {
                            continue;
                            }

                        address = f_accessor.getField(o, iField);
                        if (isLocal(address))
                            {
                            o = ensure(address);
                            if (getAndSetHeaderBit(o, MARKER_MASK, fReachableMarker) != fReachableMarker)
                                {
                                int cFields = getFieldCount(o);
                                if (cFields > 0)
                                    {
                                    // push

                                    if (iTop + 1 == state.length)
                                        {
                                        // grown state
                                        int[] newState = new int[state.length * 2];
                                        System.arraycopy(state, 0, newState, 0, state.length);
                                        state = newState;
                                        }

                                    state[++iTop] = slot(address);
                                    state[++iTop] = cFields;
                                    }
                                }
                            }
                        }
                    }
                while ((iTop -= 2) > 0); // pop
                }
            }

        return state;
        }


    private int[] handleWeakSweep(V weak, int nWeak, int[] anNotify, int nNotifyTop, boolean fReachableMarker)
        {
        // clear weak refs as necessary
        long pReferent = f_accessor.getField(weak, WEAK_REFERENT_FIELD);
        if (isLocal(pReferent))
            {
            V referent = m_aObjects[slot(pReferent)];
            if (referent == null || getHeaderBit(referent, MARKER_MASK) != fReachableMarker)
                {
                f_accessor.setField(weak, WEAK_REFERENT_FIELD, NULL); // clear referent
                getAndSetHeaderBit(weak, WEAK_MASK, false); // once cleared it can be treated as a "normal" object
                if (getFieldCount(weak) > WEAK_NOTIFIER_FIELD && f_accessor.getField(weak, WEAK_NOTIFIER_FIELD) != NULL)
                    {
                    // enqueue the weak-ref
                    if (anNotify == null)
                        {
                        anNotify = new int[8];
                        }
                    else if (nNotifyTop > anNotify.length)
                        {
                        int[] anWeaksNew = new int[anNotify.length * 2];
                        System.arraycopy(anWeaksNew, 0, anWeaksNew, 0, anNotify.length);
                        anNotify = anWeaksNew;
                        }
                    anNotify[nNotifyTop] = nWeak;
                    }
                }
            }

        return anNotify;
        }

    /**
     * Expand the size of this space.
     */
    private void grow()
            throws OutOfMemoryError
        {
        int[] anFreeSlots = m_anFreeSlots;
        V[] aObjects = m_aObjects;

        int capOld = anFreeSlots.length;
        int capNew = capOld * 2;
        if (capNew == 0)
            {
            capNew = Integer.MAX_VALUE;
            }
        int[] anFreeSlotsNew = new int[capNew];

        @SuppressWarnings("unchecked")
        V[] aObjectsNew = (V[]) new Object[capNew];

        System.arraycopy(aObjects, 0, aObjectsNew, 0, aObjects.length);
        for (int i = 0, c = capNew - capOld; i < c; ++i)
            {
            anFreeSlotsNew[i] = capOld + i;
            }

        this.m_nTopFree = capNew - capOld;
        this.m_anFreeSlots = anFreeSlotsNew;
        this.m_aObjects = aObjectsNew;
        }


    /**
     * Return the address of a slot.
     *
     * @param slot the slot
     * @return the address
     */
    private long address(int slot)
        {
        return (((long) slot) << 32) | 1L;
        }

    /**
     * Return the slot for a given address.
     *
     * @param address the address
     * @return the slot
     */
    private int slot(long address)
        {
        return (int) (address >>> 32);
        }

    /**
     * Return {@code true} if the address represents a local address.
     *
     * @param address the address
     * @return {@code true} if the address represents a local address
     */
    private boolean isLocal(long address)
        {
        return (address & 1) == 1;
        }

    /**
     * Get a single bit from the header.
     *
     * @param o    the object to query
     * @param mask the header mask to check against
     * @return the marker
     */
    private boolean getHeaderBit(V o, long mask)
        {
        return (f_accessor.getHeader(o) & mask) != 0;
        }

    /**
     * Get and set the header bit for the given mask.
     *
     * @param o     the object
     * @param mask  the header mask
     * @param value the updated bit value
     * @return the old bit value
     */
    private boolean getAndSetHeaderBit(V o, long mask, boolean value)
        {
        long header = f_accessor.getHeader(o);
        if (value)
            {
            f_accessor.setHeader(o, header | mask);
            }
        else
            {
            f_accessor.setHeader(o, header & ~mask);
            }

        return (header & mask) != 0;
        }

    /**
     * Return the number of fields contained in the object.
     *
     * @param o the object
     * @return the field count
     */
    private int getFieldCount(V o)
        {
        // TODO: get from class type rather then burning bits in the header
        return (int) ((f_accessor.getHeader(o) & FIELD_COUNT_MASK) >>> FIELD_COUNT_SHIFT);
        }

    /**
     * Set the field count for an object.
     *
     * @param o the object
     * @param cFields the field count
     */
    private void setFieldCount(V o, int cFields)
        {
        if (cFields < 0 || cFields > (FIELD_COUNT_MASK >> FIELD_COUNT_SHIFT))
            {
            throw new IllegalArgumentException();
            }

        f_accessor.setHeader(o, (f_accessor.getHeader(o) & ~FIELD_COUNT_MASK) | ((long) cFields << FIELD_COUNT_SHIFT));
        }

    /**
     * The bit-mask in the header used to mark the object as being reachable.
     */
    static final long MARKER_MASK = 0x1;

    /**
     * The bit-mask in the header used to indicate if the object represents a "weak" ref which requires special
     * handling.
     */
    static final long WEAK_MASK = 0x2;

    /**
     * The bit-mask in the header encoding the
     */
    static final long FIELD_COUNT_MASK = 0xFFC;

    /**
     * The right shift of the post masked {@link #FIELD_COUNT_MASK} to obtain the mask
     */
    static final int FIELD_COUNT_SHIFT = 2;

    /**
     * The means by which we access an objects storage.
     */
    final ObjectManager<V> f_accessor;

    /**
     * The listener to notify when weak-refs become clearable
     */
    final LongConsumer f_clearedListener;

    /**
     * The size in bytes we will try to stay below.
     */
    final long f_cbLimitSoft;

    /**
     * The maximum size (in bytes) we can grow to.
     */
    final long f_cbLimitHard;

    /**
     * The amount of memory retained by this {@link MarkAndSweepGcSpace}.
     */
    long m_cBytes;

    /**
     * The index of the top element in {@link #m_nTopFree} that represents a free slot in {@link #m_aObjects}.
     */
    int m_nTopFree;

    /**
     * The slots available in {@link #m_aObjects}
     */
    int[] m_anFreeSlots = new int[1024];

    /**
     * References to our objects, based on their {@link #slot(long)} address.
     */
    @SuppressWarnings("unchecked")
    V[] m_aObjects = (V[]) new Object[m_anFreeSlots.length];

    /**
     * The "gc" roots for this space.
     */
    final Set<Supplier<? extends PrimitiveIterator.OfLong>> f_setRoots = new HashSet<>();

    /**
     * The marker to set on newly allocated objects
     */
    boolean m_fAllocationMarker;
    }
