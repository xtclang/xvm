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
        implements GcSpace<V>
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
    public FieldAccessor<V> accessor()
        {
        return f_accessor;
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
    public V get(long address)
            throws SegFault
        {
        if (address == NULL)
            {
            return null;
            }
        else if (!isLocal(address))
            {
            throw new SegFault();
            }

        int slot = slot(address);
        if (slot < 0 || slot > m_aObjects.length)
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
        for (var root : f_setRoots)
            {
            for (var liter = root.get(); liter.hasNext(); )
                {
                walkAndMark(liter.next(), fReachableMarker);
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
     * @param po               the source object address
     * @param fReachableMarker the value to mark the objects with
     * @return the weaks mapping, or {@code null}
     */
    private void walkAndMark(long po, boolean fReachableMarker)
        {
        V o = get(po);
        // TODO: dynamically switch from recursive to non-recursive based on depth
        if (o != null && getAndSetHeaderBit(o, MARKER_MASK, fReachableMarker) != fReachableMarker)
            {
            boolean fWeak = getHeaderBit(o, WEAK_MASK);
            for (int i = fWeak ? 1 : 0, c = f_accessor.getFieldCount(o); i < c; ++i)
                {
                long address = f_accessor.getField(o, i);
                if (isLocal(address))
                    {
                    walkAndMark(address, fReachableMarker);
                    }
                }
            }
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
                if (f_accessor.getFieldCount(weak) > WEAK_NOTIFIER_FIELD && f_accessor.getField(weak, WEAK_NOTIFIER_FIELD) != NULL)
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
    private boolean getHeaderBit(V o, int mask)
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
    private boolean getAndSetHeaderBit(V o, int mask, boolean value)
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
     * The bit-mask in the header used to mark the object as being reachable.
     */
    static final int MARKER_MASK = 1;

    /**
     * The bit-mask in the header used to indicate if the object represents a "weak" ref which requires special
     * handling.
     */
    static final int WEAK_MASK = 2;

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
