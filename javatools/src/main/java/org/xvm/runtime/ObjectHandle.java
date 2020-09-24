package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.xObject;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.util.ListMap;


/**
 * Runtime operates on Object handles holding the struct references or the values themselves
 * for the following types:
 *  Bit, Boolean, Char, Int, UInt, Nullable.Null, and optionally for some Tuples
 *
 * Note, that the equals() and hashCode() methods should be only for immutable handles.
 */
public abstract class ObjectHandle
        implements Cloneable
    {
    protected TypeComposition m_clazz;
    protected boolean m_fMutable = false;

    public static final ObjectHandle DEFAULT = new ObjectHandle(null)
        {
        @Override
        public TypeConstant getType()
            {
            return null;
            }

        @Override
        public String toString()
            {
            return "<default>";
            }
        };

    protected ObjectHandle(TypeComposition clazz)
        {
        m_clazz = clazz;
        }

    /**
     * Clone this handle using the specified TypeComposition.
     *
     * @param clazz  the TypeComposition to mask/reveal this handle as
     *
     * @return the new handle
     */
    public ObjectHandle cloneAs(TypeComposition clazz)
        {
        try
            {
            ObjectHandle handle = (ObjectHandle) super.clone();
            handle.m_clazz = clazz;
            return handle;
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException();
            }
        }

    /**
     * Reveal this handle using the "inception" type.
     *
     * @return the "fully accessible" handle
     */
    public ObjectHandle revealOrigin()
        {
        return m_clazz.ensureOrigin(this);
        }

    public boolean isMutable()
        {
        return m_fMutable;
        }

    public void makeImmutable()
        {
        m_fMutable = false;
        }

    /**
     * @return null iff all the fields are assigned; a list of unassigned names otherwise
     */
    public List<String> validateFields()
        {
        return null;
        }

    public boolean isSelfContained()
        {
        return false;
        }

    /**
     * @return the TypeComposition for this handle
     */
    public TypeComposition getComposition()
        {
        return m_clazz;
        }

    /**
     * @return the underlying template for this handle
     */
    public ClassTemplate getTemplate()
        {
        return m_clazz.getTemplate();
        }

    /**
     * @return the OpSupport for the inception type of this handle
     */
    public OpSupport getOpSupport()
        {
        return m_clazz.getSupport();
        }

    /**
     * @return the revealed type of this handle
     */
    public TypeConstant getType()
        {
        TypeConstant type = m_clazz.getType();
        return isMutable()
                ? type
                : type.freeze();
        }

    public ObjectHandle ensureAccess(Constants.Access access)
        {
        return m_clazz.ensureAccess(this, access);
        }

    /**
     * Check whether or not the property referred by the specified constant has a custom code or
     * Ref-annotation.
     *
     * @param idProp  the property to check
     *
     * @return true iff the specified property has custom code or is Ref-annotated
     */
    public boolean isInflated(PropertyConstant idProp)
        {
        return m_clazz.isInflated(idProp.getNestedIdentity());
        }

    /**
     * Check whether or not the property referred by the specified constant has an injected value.
     *
     * @param idProp  the property to check
     *
     * @return true iff the specified property has an injected value
     */
    public boolean isInjected(PropertyConstant idProp)
        {
        return m_clazz.isInjected(idProp);
        }

    /**
     * Check whether or not the property referred by the specified constant has to be treated as
     * an atomic value.
     *
     * @param idProp  the property to check
     *
     * @return true iff the specified property has an atomic value
     */
    public boolean isAtomic(PropertyConstant idProp)
        {
        return m_clazz.isAtomic(idProp);
        }

    /**
     * @return true iff the handle is a non-constant object that is allowed to be passed across
     *         service boundaries
     */
    public boolean isService()
        {
        return getTemplate().isService();
        }

    /**
     * @return true iff the handle represents a struct
     */
    public boolean isStruct()
        {
        return m_clazz.isStruct();
        }

    /**
     * @return true iff the handle itself could be used for the equality check
     */
    public boolean isNativeEqual()
        {
        return true;
        }

    /**
     * Mask this handle to the specified type on behalf of the specified container.
     *
     * @return a new handle for this object masked to the specified type or null if the
     *         request cannot be fulfilled
     */
    public ObjectHandle maskAs(Container owner, TypeConstant typeAs)
        {
        return this;
        }

    /**
     * Reveal this handle as the specified type on the context of the specified frame.
     *
     * @return a new handle for this object revealed as the specified type or null if the
     *         request cannot be fulfilled
     */
    public ObjectHandle revealAs(Frame frame, TypeConstant typeAs)
        {
        return this;
        }

    /**
     * If a handle supports deferred call - continue with the processing and place the deferred
     * value on the caller's stack.
     *
     * @param frameCaller   the caller frame
     * @param continuation  the continuation to resume to
     *
     * @return Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION
     */
    public int proceed(Frame frameCaller, Frame.Continuation continuation)
        {
        throw new IllegalStateException("Not deferred");
        }

    /**
     * @return the result of comparison (only for isNativeEqual() handles)
     */
    public int compareTo(ObjectHandle that)
        {
        throw new UnsupportedOperationException(getClass() + " cannot compare");
        }

    @Override
    public int hashCode()
        {
        if (isNativeEqual())
            {
            throw new UnsupportedOperationException(getClass() + " must implement \"hashCode()\"");
            }

        return System.identityHashCode(this);
        }

    @Override
    public boolean equals(Object obj)
        {
        if (isNativeEqual())
            {
            throw new UnsupportedOperationException(getClass() + " must implement \"equals()\"");
            }

        // we don't use this for natural equality check
        return this == obj;
        }

    @Override
    public String toString()
        {
        return "(" + m_clazz + ") ";
        }

    public static class GenericHandle
            extends ObjectHandle
        {
        public GenericHandle(TypeComposition clazz)
            {
            super(clazz);

            m_fMutable = true;

            m_mapFields = clazz.initializeStructure();
            }

        public Map<Object, ObjectHandle> getFields()
            {
            return m_mapFields == null ? Collections.EMPTY_MAP : m_mapFields;
            }

        public boolean containsField(PropertyConstant idProp)
            {
            return m_mapFields != null && m_mapFields.containsKey(idProp.getNestedIdentity());
            }

        public ObjectHandle getField(PropertyConstant idProp)
            {
            return m_mapFields == null ? null : m_mapFields.get(idProp.getNestedIdentity());
            }

        public ObjectHandle getField(String sProp)
            {
            return m_mapFields == null ? null : m_mapFields.get(sProp);
            }

        public void setField(PropertyConstant idProp, ObjectHandle hValue)
            {
            if (m_mapFields == null)
                {
                m_mapFields = new ListMap<>();
                }
            m_mapFields.put(idProp.getNestedIdentity(), hValue);
            }

        public void setField(String sProp, ObjectHandle hValue)
            {
            if (m_mapFields == null)
                {
                m_mapFields = new ListMap<>();
                }
            m_mapFields.put(sProp, hValue);
            }

        public boolean containsMutableFields()
            {
            if (m_mapFields != null)
                {
                for (ObjectHandle hValue : m_mapFields.values())
                    {
                    if (hValue.isMutable())
                        {
                        return true;
                        }
                    }
                }
            return false;
            }

        @Override
        public boolean isService()
            {
            if (getTemplate().isService())
                {
                return true;
                }

            ObjectHandle hOuter = getField(OUTER);
            return hOuter != null && hOuter.isService();
            }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz)
            {
            // when we clone a struct into a non-struct, we need to update the inflated
            // RefHandles to point to a non-struct parent handle;
            // when we clone a non-struct to a struct, we need to do the opposite
            boolean fUpdateOuter = isStruct() || clazz.isStruct();

            GenericHandle hClone = (GenericHandle) super.cloneAs(clazz);

            if (fUpdateOuter && m_mapFields != null)
                {
                for (Map.Entry<Object, ObjectHandle> entry : m_mapFields.entrySet())
                    {
                    if (clazz.isInflated(entry.getKey()))
                        {
                        RefHandle    hValue = (RefHandle) entry.getValue();
                        ObjectHandle hOuter = hValue.getField(OUTER);
                        if (hOuter != null)
                            {
                            hValue.setField(OUTER, hClone);
                            }
                        }
                    }
                }
            return hClone;
            }

        @Override
        public List<String> validateFields()
            {
            List<String> listUnassigned = null;
            if (m_mapFields != null)
                {
                for (Map.Entry<Object, ObjectHandle> entry : m_mapFields.entrySet())
                    {
                    ObjectHandle hValue = entry.getValue();
                    if (hValue == null)
                        {
                        Object idProp = entry.getKey();

                        if (!getComposition().isAllowedUnassigned(idProp))
                            {
                            if (listUnassigned == null)
                                {
                                listUnassigned = new ArrayList<>();
                                }
                            listUnassigned.add(idProp.toString());
                            }
                        }
                    // no need to recurse to a field; it would throw during its own construction
                    }
                }
            return listUnassigned;
            }

        @Override
        public boolean isNativeEqual()
            {
            return false;
            }

        @Override
        public GenericHandle maskAs(Container owner, TypeConstant typeAs)
            {
            TypeComposition clzAs = getComposition().maskAs(typeAs);
            if (clzAs != null)
                {
                if (owner == m_owner)
                    {
                    return this;
                    }

                GenericHandle hClone = (GenericHandle) cloneAs(clzAs);
                hClone.m_owner = owner;
                return hClone;
                }
            return null;
            }

        @Override
        public GenericHandle revealAs(Frame frame, TypeConstant typeAs)
            {
            if (m_owner != null && m_owner != frame.f_context.f_container)
                {
                // only the owner can reveal
                return null;
                }

            TypeComposition clzAs = getComposition().revealAs(typeAs);
            if (clzAs != null)
                {
                // TODO: consider holding to the original object and returning it
                return (GenericHandle) cloneAs(clzAs);
                }
            return null;
            }

        public static boolean compareIdentity(GenericHandle h1, GenericHandle h2)
            {
            if (h1 == h2)
                {
                return true;
                }

            if (h1.isMutable() || h2.isMutable())
                {
                return false;
                }

            Map<Object, ObjectHandle> map1 = h1.m_mapFields;
            Map<Object, ObjectHandle> map2 = h2.m_mapFields;

            if (map1 == map2)
                {
                return true;
                }

            if (map1.size() != map2.size())
                {
                return false;
                }

            for (Object idProp : map1.keySet())
                {
                ObjectHandle hV1 = map1.get(idProp);
                ObjectHandle hV2 = map2.get(idProp);

                // TODO: need to prevent a potential infinite loop
                ClassTemplate template = hV1.getTemplate();
                if (template != hV2.getTemplate() || !template.compareIdentity(hV1, hV2))
                    {
                    return false;
                    }
                }

            return true;
            }

        // keyed by the property name or a NestedIdentity
        private Map<Object, ObjectHandle> m_mapFields;

        // not null only if this object was injected or explicitly "masked as"
        protected Container m_owner;

        /**
         * Synthetic property holding a reference to a parent instance.
         */
        public final static String OUTER = "$outer";
        }

    public static class ExceptionHandle
            extends GenericHandle
        {
        protected WrapperException m_exception;

        public ExceptionHandle(TypeComposition clazz, boolean fInitialize, Throwable eCause)
            {
            super(clazz);

            if (fInitialize)
                {
                m_exception = eCause == null ?
                        new WrapperException() : new WrapperException(eCause);
                }
            }

        public WrapperException getException()
            {
            return new WrapperException();
            }

        public class WrapperException
                extends Exception
            {
            public WrapperException()
                {
                super();
                }

            public WrapperException(Throwable cause)
                {
                super(cause);
                }

            public ExceptionHandle getExceptionHandle()
                {
                return ExceptionHandle.this;
                }

            @Override
            public String toString()
                {
                return getExceptionHandle().toString();
                }
            }
        }

    // anything that fits in a long
    public static class JavaLong
            extends ObjectHandle
        {
        protected long m_lValue;

        public JavaLong(TypeComposition clazz, long lValue)
            {
            super(clazz);
            m_lValue = lValue;
            }

        @Override
        public boolean isSelfContained()
            {
            return true;
            }

        public long getValue()
            {
            return m_lValue;
            }

        @Override
        public int hashCode()
            {
            return Long.hashCode(m_lValue);
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            return Long.compare(m_lValue, ((JavaLong) that).m_lValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof JavaLong
                && m_lValue == ((JavaLong) obj).m_lValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_lValue;
            }
        }

    // abstract array handle
    public abstract static class ArrayHandle
            extends ObjectHandle
        {
        public xArray.Mutability m_mutability;
        public int m_cSize;

        protected ArrayHandle(TypeComposition clzArray, xArray.Mutability mutability)
            {
            super(clzArray);

            m_fMutable   = mutability != xArray.Mutability.Constant;
            m_mutability = mutability;
            }

        abstract public int getCapacity();
        abstract public void setCapacity(int nCapacity);
        abstract public ObjectHandle getElement(int ix);
        abstract public void deleteElement(int ix);

        @Override
        public void makeImmutable()
            {
            super.makeImmutable();

            m_mutability = xArray.Mutability.Constant;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_mutability + ", size=" + m_cSize;
            }
        }

    // native handle that holds a reference to a Constant from the ConstantPool
    public static class ConstantHandle
            extends ObjectHandle
        {
        public ConstantHandle(Constant constant)
            {
            super(xObject.CLASS);

            assert constant != null;
            m_const = constant;
            }

        public Constant get()
            {
            return m_const;
            }

        @Override
        public String toString()
            {
            return m_const.toString();
            }

        private Constant m_const;
        }

    /**
     * DeferredCallHandle represents a deferred action, such as a property access or a method call,
     * which would place the result of that action on the corresponding frame's stack.
     *
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredCallHandle
            extends ObjectHandle
        {
        protected final Frame           f_frameNext;
        protected final ExceptionHandle f_hException;

        public DeferredCallHandle(Frame frameNext)
            {
            super(null);

            f_frameNext  = frameNext;
            f_hException = frameNext.m_hException;
            }

        public DeferredCallHandle(ExceptionHandle hException)
            {
            super(null);

            f_frameNext  = null;
            f_hException = hException;
            }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation)
            {
            if (f_hException == null)
                {
                Frame frameNext = f_frameNext;
                frameNext.addContinuation(continuation);
                return frameCaller.call(frameNext);
                }

            frameCaller.m_hException = f_hException;
            return Op.R_EXCEPTION;
            }

        public void addContinuation(Frame.Continuation continuation)
            {
            if (f_hException == null)
                {
                f_frameNext.addContinuation(continuation);
                }
            }

        @Override
        public String toString()
            {
            return f_hException == null
                ? "Deferred call: " + f_frameNext
                : "Deferred exception: " + f_hException;
            }
        }

    /**
     * DeferredPropertyHandle represents a deferred property access, which would place the result
     * of that action on the corresponding frame's stack.
     *
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredPropertyHandle
            extends DeferredCallHandle
        {
        private final PropertyConstant f_idProp;

        public DeferredPropertyHandle(PropertyConstant idProp)
            {
            super((ExceptionHandle) null);

            f_idProp = idProp;
            }

        @Override
        public void addContinuation(Frame.Continuation continuation)
            {
            throw new UnsupportedOperationException();
            }

        public PropertyConstant getProperty()
            {
            return f_idProp;
            }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation)
            {
            ObjectHandle hThis = frameCaller.getThis();

            switch (hThis.getTemplate().getPropertyValue(frameCaller, hThis, f_idProp, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return continuation.proceed(frameCaller);

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(continuation);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        @Override
        public String toString()
            {
            return "Deferred property access: " + f_idProp.getName();
            }
        }

    /**
     * DeferredSingletonHandle represents a deferred singleton calculation, which would place the
     * result of that action on the corresponding frame's stack.
     *
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredSingletonHandle
            extends DeferredCallHandle
        {
        private final SingletonConstant f_constSingleton;

        public DeferredSingletonHandle(SingletonConstant constSingleton)
            {
            super((ExceptionHandle) null);

            f_constSingleton = constSingleton;
            }

        @Override
        public void addContinuation(Frame.Continuation continuation)
            {
            throw new UnsupportedOperationException();
            }

        public SingletonConstant getConstant()
            {
            return f_constSingleton;
            }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation)
            {
            return Utils.initConstants(frameCaller, Collections.singletonList(f_constSingleton),
                frame ->
                    {
                    frame.pushStack(f_constSingleton.getHandle());
                    return continuation.proceed(frame);
                    });
            }

        @Override
        public String toString()
            {
            return "Deferred initialization for " + f_constSingleton;
            }
        }

    /**
     * DeferredArrayHandle represents a deferred array initialization, which would place the array
     * handle on the corresponding frame's stack.
     *
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredArrayHandle
            extends DeferredCallHandle
        {
        private final ClassComposition f_clzArray;
        private final ObjectHandle[]   f_ahValue;

        public DeferredArrayHandle(ClassComposition clzArray, ObjectHandle[] ahValue)
            {
            super((ExceptionHandle) null);

            f_clzArray = clzArray;
            f_ahValue  = ahValue;
            }

        @Override
        public TypeConstant getType()
            {
            TypeConstant type = f_clzArray.getType();
            return isMutable()
                    ? type
                    : type.freeze();
            }

        @Override
        public ObjectHandle revealOrigin()
            {
            return this;
            }

        @Override
        public void addContinuation(Frame.Continuation continuation)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation)
            {
            Frame.Continuation stepAssign = frame -> frame.pushStack(
                ((xArray) f_clzArray.getTemplate()).createArrayHandle(f_clzArray, f_ahValue));

            switch (new Utils.GetArguments(f_ahValue, stepAssign).doNext(frameCaller))
                {
                case Op.R_NEXT:
                    return continuation.proceed(frameCaller);

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(continuation);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        @Override
        public String toString()
            {
            return "Deferred array initialization: " + f_clzArray.getType();
            }
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
