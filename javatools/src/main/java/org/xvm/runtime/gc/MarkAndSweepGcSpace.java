package org.xvm.runtime.gc;


import java.util.*;
import java.util.function.*;

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
     * @param accessor the accessor of accessing the contents of an object
     * @param clearedListener a function to invoke when a "weak" ref has been cleared
     */
    public MarkAndSweepGcSpace(ObjectStorage<V> accessor, Consumer<V> clearedListener) {
        this (accessor, clearedListener, Long.MAX_VALUE, Long.MAX_VALUE);
    }

   /**
    * Construct a {@link MarkAndSweepGcSpace} with limits.
    *
    * @param accessor the accessor of accessing the contents of an object
    * @param clearedListener a function to invoke when a "weak" ref which have been cleared
    * @param cbLimitSoft the byte size to try to stay within
    * @param cbLimitHard the maximum allowable byte size
    */
    public MarkAndSweepGcSpace(ObjectStorage<V> accessor,
                               Consumer<V> clearedListener,
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
    public long allocate(Supplier<? extends V> constructor, boolean weak)
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

        V resource = constructor.get();
        getAndSetHeaderBit(resource, MARKER_MASK, fAllocationMarker);
        if (weak)
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
    public void add(Root root)
        {
        f_setRoots.add(root);
        }

    @Override
    public void remove(Root root)
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
        Map<Long, Collection<V>> weaks = null;
        boolean fReachableMarker = !fAllocationMarker;
        fAllocationMarker = fReachableMarker;
        for (Root root : f_setRoots)
            {
            for (var liter = root.collectables(); liter.hasNext(); )
                {
                weaks = walkAndMark(get(liter.next()), fReachableMarker, weaks);
                }
            }

        V[] aObjects = m_aObjects;
        int[] anFreeSlots = m_anFreeSlots;
        for (int i = 0; i < aObjects.length; ++i)
            {
            V o = aObjects[i];
            if (o != null && !getHeaderBit(o, MARKER_MASK) == fReachableMarker)
                {
                aObjects[i] = null;
                anFreeSlots[++m_nTopFree] = i;
                m_cBytes -= f_accessor.getByteSize(o);
                if (weaks != null && getHeaderBit(o, WEAK_MASK))
                    {
                    handleWeakSweep(o, weaks);
                    }
                }
            }

        if (weaks != null)
            {
            handleWeakPostSweep(weaks);
            }
        }

    /**
     * Walk and mark the graph of objects reachable from a given object.
     *
     * @param o                the source object
     * @param fReachableMarker the value to mark the objects with
     * @param weaks            the mapping of referants to their weak-refs, or {@code null}
     *
     * @return the weaks mapping, or {@code null}
     */
    private Map<Long, Collection<V>> walkAndMark(V o, boolean fReachableMarker, Map<Long, Collection<V>> weaks)
        {
        // TODO: dynamically switch from recursive to non-recursive based on depth
        if (o != null && getAndSetHeaderBit(o, MARKER_MASK, fReachableMarker) != fReachableMarker)
            {
            boolean fWeak = getHeaderBit(o, WEAK_MASK);
            if (fWeak)
                {
                weaks = handleWeakMark(o, weaks);
                }

            for (int i = fWeak ? 1 : 0, c = f_accessor.getFieldCount(o); i < c; ++i)
                {
                long address = f_accessor.getField(o, i);
                if (isLocal(address))
                    {
                    weaks = walkAndMark(get(address), fReachableMarker, weaks);
                    }
                }
            }

        return weaks;
        }

    /**
     * Handle the tracking of weak-refs during the marking phase.
     *
     * @param o the weak ref
     * @param weaks the map of weak referants to their weak refs, or {@code null}
     * @return the weaks map, or {@code null}
     */
    private Map<Long, Collection<V>> handleWeakMark(V o, Map<Long, Collection<V>> weaks)
        {
        long referant = f_accessor.getField(o, 0);
        if (referant != NULL)
            {
            if (weaks == null)
                {
                weaks = new HashMap<>();
                }

            weaks.compute(referant, (k, v) -> {
            if (v == null)
                {
                v = new ArrayList<>(1);
                }

            v.add(o);
            return v;
            });
            }

        return weaks;
        }
    /**
     * Handle the post-sweeping of weak-refs.
     *
     * @param weaks the mapping of address to the weak refs which refer to them
     */
    private void handleWeakSweep(V o, Map<Long, Collection<V>> weaks)
        {
        long referant = f_accessor.getField(o, 0);
        if (referant != NULL)
            {
            // a weak-ref itself is being collected, if its referant is also being collected we need to be
            // sure we don't perform the weak-ref notification on the now dead ref
            weaks.computeIfPresent(referant, (k, v) -> v.remove(o) && v.isEmpty() ? null : v);
            }
        }

    /**
     * Handle the post-sweeping for weak-refs.
     *
     * @param weaks the mapping of address to the weak refs which refer to them
     */
    private void handleWeakPostSweep(Map<Long, Collection<V>> weaks)
        {
        Object[] aObjects = m_aObjects;
        // first pass over weaks, clear and retain only those which reference the dead
        for (var iter = weaks.entrySet().iterator(); iter.hasNext(); )
            {
            Map.Entry<Long, Collection<V>> entry = iter.next();
            if (m_aObjects[slot(entry.getKey())] == null)
                {
                for (V weak : entry.getValue())
                    {
                    // clear the ref
                    f_accessor.setField(weak, 0, 0);
                    }
                }
            else
                {
                iter.remove();
                }
            }

        // second pass over weaks, it is now safe for callback, notify listener
        for (Collection<V> weakRefs : weaks.values())
            {
            for (V weak : weakRefs)
                {
                f_clearedListener.accept(weak);
                }
            }
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
     * @param o the object to query
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
     * @param o the object
     * @param mask the header mask
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
    final ObjectStorage<V> f_accessor;

    /**
     * The listener to notify when weak-refs become clearable
     */
    final Consumer<V> f_clearedListener;

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
    final Set<Root> f_setRoots = new HashSet<>();

    /**
     * The marker to set on newly allocated objects
     */
    boolean fAllocationMarker;
    }
