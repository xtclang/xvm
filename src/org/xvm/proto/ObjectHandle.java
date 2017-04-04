package org.xvm.proto;

import org.xvm.util.ListMap;

import java.util.Map;

/**
 * Runtime operates on Object handles holding the struct references or the values themselves
 * for the following types:
 *  Bit, Boolean, Char, Int, UInt, Nullable.Null, and optionally for some Tuples
 *
 * @author gg 2017.02.15
 */
public abstract class ObjectHandle
        implements Cloneable
    {
    final public TypeComposition f_clazz;
    public Type m_type;

    protected ObjectHandle(TypeComposition clazz)
        {
        this(clazz, clazz.ensurePublicType());
        }

    protected ObjectHandle(TypeComposition clazz, Type type)
        {
        f_clazz = clazz;
        m_type = type;
        }

    public ObjectHandle cloneHandle()
        {
        try
            {
            return (ObjectHandle) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException();
            }
        }

    public <T extends ObjectHandle> boolean isAssignableTo(Class<T> clz)
        {
        return clz.isAssignableFrom(getClass());
        }

    public <T extends ObjectHandle> T as(Class<T> clz)
        {
        return (T) this;
        }

    @Override
    public String toString()
        {
        return "(" + f_clazz + ") ";
        }

    public static class GenericHandle
            extends ObjectHandle
        {
        // keyed by the property name
        protected Map<String, ObjectHandle> m_mapFields = new ListMap<>();

        public GenericHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public GenericHandle(TypeComposition clazz, Type type)
            {
            super(clazz, type);
            }

        public void createFields()
            {
            f_clazz.f_template.forEachProperty(pt ->
                {
                if (!pt.isReadOnly())
                    {
                    m_mapFields.put(pt.f_sName, null);
                    }
                });
            }
        @Override
        public String toString()
            {
            return super.toString() + m_mapFields;
            }
        }

    public static class JavaDelegate
            extends ObjectHandle
        {
        protected Object m_oDelegate;

        public JavaDelegate(TypeComposition clazz)
            {
            super(clazz);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_oDelegate;
            }
        }

    // anything that fits in a long
    public static class JavaLong
            extends ObjectHandle
        {
        protected long m_lValue = UNASSIGNED;

        public JavaLong(TypeComposition clazz)
            {
            super(clazz);
            }

        public JavaLong(TypeComposition clazz, long lValue)
            {
            super(clazz);
            m_lValue = lValue;
            }

        public long getValue()
            {
            return m_lValue;
            }

        public void assign(long lValue)
            {
            assert m_lValue == UNASSIGNED;
            m_lValue = lValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_lValue;
            }
        private final static long UNASSIGNED = 0xBADBAD0BADBADBADl;
        }


    // ----- DEFERRED ----

    public static long createHandle(int nTypeId, int nIdentityId, boolean fMutable)
        {
        assert (nTypeId & ~MASK_TYPE) == 0;
        return (((long) nTypeId) & MASK_TYPE) | ((long) nIdentityId << 32 & MASK_IDENTITY) | HANDLE;
        }

    /**
     * @return true iff the specified long value could be used "in place" of the handle
     */
    public static boolean isNaked(long lValue)
        {
        return (lValue & HANDLE) == 0;
        }

    /**
     * @return true iff the specified double value could be used "in place" of the handle
     */
    public static boolean isNaked(double dValue)
        {
        return (Double.doubleToRawLongBits(dValue) & HANDLE) == 0;
        }

    public static int getTypeId(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (int) (lHandle & MASK_TYPE);
        }

    public static int getIdentityId(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (int) (lHandle & MASK_IDENTITY >>> 32);
        }

    public static boolean isImmutable(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & STYLE_IMMUTABLE) != 0;
        }

    public static boolean isService(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & STYLE_SERVICE) != 0;
        }

    public static boolean isFunction(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & FUNCTION) != 0;
        }

    public static boolean isGlobal(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & GLOBAL) != 0;
        }

    // zero identity indicates a non-initialized handle
    public static boolean isAssigned(long lHandle)
        {
        return getIdentityId(lHandle) != 0;
        }

    // bits 0-26: type id
    private final static long MASK_TYPE       = 0x07FF_FFFF;

    // bit 26: always set unless the value is a naked Int or Double
    private final static long HANDLE          = 0x0800_0000;

    // bit 27: reserved
    private final static long BIT_27          = 0x1000_0000;

    // bits 28-29 style
    private final static long MASK_STYLE      = 0x3000_0000;
    private final static long STYLE_MUTABLE   = 0x0000_0000;
    private final static long STYLE_IMMUTABLE = 0x1000_0000;
    private final static long STYLE_SERVICE   = 0x2000_0000;
    private final static long STYLE_RESERVED  = 0x3000_0000;

    // bit 30: function
    private final static long FUNCTION        = 0x4000_0000L;

    // bit 31: global - if indicates that the object resides in the global heap; must be immutable
    private final static long GLOBAL = 0x8000_0000L;

    // bits 32-63: identity id
    private final static long MASK_IDENTITY   = 0xFFFF_FFFF_0000_0000L;

    }
