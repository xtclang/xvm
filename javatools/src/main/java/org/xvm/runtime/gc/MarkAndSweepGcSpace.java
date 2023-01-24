package org.xvm.runtime.gc;

import org.xvm.util.ShallowSizeOf;

import java.util.HashSet;
import java.util.Set;
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
     * The means by which we access an objects storage.
     */
    final ObjectStorage<V> f_accessor;

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

    /**
     * Construct a {@link MarkAndSweepGcSpace}.
     *
     * @param accessor the accessor of accessing the contents of an object
     */
    public MarkAndSweepGcSpace(ObjectStorage<V> accessor) {
        this (accessor, Long.MAX_VALUE, Long.MAX_VALUE);
    }

   /**
    * Construct a {@link MarkAndSweepGcSpace} with limits.
    *
    * @param accessor the accessor of accessing the contents of an object
    * @param cbLimitSoft the byte size to try to stay within
    * @param cbLimitHard the maximum allowable byte size
    */
    public MarkAndSweepGcSpace(ObjectStorage<V> accessor,
                               long cbLimitSoft,
                               long cbLimitHard)
        {
        f_accessor = accessor;
        f_cbLimitSoft = cbLimitSoft;
        f_cbLimitHard = cbLimitHard;

        for (int i = 0; i < m_anFreeSlots.length; ++i)
            {
            m_anFreeSlots[i] = i;
            }
        m_nTopFree = m_anFreeSlots.length - 1;
        }

    @Override
    public long allocate(Supplier<? extends V> constructor)
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
        f_accessor.getAndSetMarker(resource, fAllocationMarker);
        m_cBytes += ShallowSizeOf.object(resource);

        int slot = m_anFreeSlots[m_nTopFree--];
        m_aObjects[slot] = resource;
        return address(slot);
        }

    @Override
    public V get(long address)
            throws SegFault
        {
        if (isNull(address))
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
        boolean fReachableMarker = !fAllocationMarker;
        fAllocationMarker = fReachableMarker;
        for (Root root : f_setRoots)
            {
            for (var liter = root.collectables(); liter.hasNext(); )
                {
                walkAndMark(get(liter.next()), fReachableMarker);
                }
            }

        V[] aVS = m_aObjects;
        int[] anFreeSlots = m_anFreeSlots;
        for (int i = 0; i < aVS.length; ++i)
            {
            V o = aVS[i];
            if (o != null && !f_accessor.getMarker(o) == fReachableMarker)
                {
                aVS[i] = null;
                anFreeSlots[++m_nTopFree] = i;
                m_cBytes -= ShallowSizeOf.object(o);
                }
            }
        }

    /**
     * Walk and mark the graph of objects reachable from a given object.
     *
     * @param o                the source object
     * @param fReachableMarker the value to mark the objects with
     */
    private void walkAndMark(V o, boolean fReachableMarker)
        {
        if (o != null && f_accessor.getAndSetMarker(o, fReachableMarker) != fReachableMarker)
            {
            for (int i = 0, c = f_accessor.getFieldCount(o); i < c; ++i)
                {
                long address = f_accessor.getField(o, i);
                if (isLocal(address))
                    {
                    walkAndMark(get(address), fReachableMarker);
                    }
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
     * Return {@code true} if the address represents a local address.
     *
     * @param address the address
     * @return {@code true} if the address represents a local address
     */
    private boolean isNull(long address)
        {
        return address == 0;
        }
    }
