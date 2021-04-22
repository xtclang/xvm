package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;

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
        return getComposition().ensureOrigin(this);
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
        return getComposition().getTemplate();
        }

    /**
     * @return the OpSupport for the inception type of this handle
     */
    public OpSupport getOpSupport()
        {
        return getComposition().getSupport();
        }

    /**
     * @return the revealed type of this handle
     */
    public TypeConstant getType()
        {
        TypeConstant type = getComposition().getType();
        return isMutable()
                ? type
                : type.freeze();
        }

    /**
     * Some handles may carry a type that belongs to a "foreign" type system. As a general rule,
     * that type could be used *only* for an "isA()" evaluation.
     *
     * @return a TypeConstant that *may* belong to a "foreign" type system
     */
    public TypeConstant getUnsafeType()
        {
        return getType();
        }

    public ObjectHandle ensureAccess(Constants.Access access)
        {
        return getComposition().ensureAccess(this, access);
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
        return getComposition().isInflated(idProp.getNestedIdentity());
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
        return getComposition().isInjected(idProp);
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
        return getComposition().isAtomic(idProp);
        }

    /**
     * Check if this handle could be passed "as is" to a service context of the specified container.
     *
     * @param container  (optional) the "receiving" container
     *
     * @return true iff the handle is an object that is allowed to be passed across service/container
     *         boundaries (an immutable, a service or an object that has all pass-through fields)
     */
    public boolean isPassThrough(Container container)
        {
        if (isService())
            {
            return true;
            }

        if (isMutable())
            {
            return false;
            }

        if (container == null)
            {
            return true;
            }

        return isShared(container.getModule().getConstantPool(), null);
        }

    /**
     * Check if this handle belongs to the same type system as the one represented by the
     * specified ConstantPool.
     *
     * @param poolThat    the pool representing the "receiving" container
     * @param mapVisited  the identity hash map of visited objects
     *
     * @return true iff this object's type is shared with that pool
     */
    protected boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
        {
        return true;
        }

    /**
     * Helper method to check if all the specified handles belongs to the same type system as the
     * one represented by the specified ConstantPool.
     *
     * @param ahValue     an array of handles to check
     * @param poolThat    the pool representing the "receiving" container
     * @param mapVisited  the identity hash map of visited objects
     *
     * @return true iff this object's type is shared with that pool
     */
    protected static boolean areShared(ObjectHandle[] ahValue, ConstantPool poolThat,
                                       Map<ObjectHandle, Boolean> mapVisited)
        {
        for (ObjectHandle field : ahValue)
            {
            if (field != null && !field.isShared(poolThat, mapVisited))
                {
                return false;
                }
            }
        return true;
        }

    /**
     * @return true iff the handle is a non-constant object for which all method invocations
     *         and properties access need to be proxied across service boundaries
     */
    public boolean isService()
        {
        return false;
        }

    /**
     * If method invocations and properties access for this handle need to be proxied across
     * service boundaries, return the corresponding ServiceHandle.
     *
     * @return a ServiceHandle ir null of this handle is "not a Service"
     */
    public ServiceHandle getService()
        {
        return null;
        }

    /**
     * @return true iff the handle represents a struct
     */
    public boolean isStruct()
        {
        return getComposition().isStruct();
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
        return "(" + getComposition() + ") ";
        }

    public static class GenericHandle
            extends ObjectHandle
        {
        public GenericHandle(TypeComposition clazz)
            {
            super(clazz);

            m_fMutable = true;

            m_aFields = clazz.initializeStructure();
            }

        public ObjectHandle[] getFields()
            {
            return m_aFields;
            }

        public boolean containsField(PropertyConstant idProp)
            {
            return getComposition().containsField(idProp);
            }

        public ObjectHandle getField(PropertyConstant idProp)
            {
            return getComposition().getFieldFromStructure(m_aFields, idProp.getNestedIdentity());
            }

        public ObjectHandle getField(String sProp)
            {
            return getComposition().getFieldFromStructure(m_aFields, sProp);
            }

        public void setField(PropertyConstant idProp, ObjectHandle hValue)
            {
            getComposition().setFieldInStructure(m_aFields, idProp.getNestedIdentity(), hValue);
            }

        public void setField(String sProp, ObjectHandle hValue)
            {
            getComposition().setFieldInStructure(m_aFields, sProp, hValue);
            }

        public Container getOwner()
            {
            return m_owner;
            }

        public void setOwner(Container owner)
            {
            m_owner = owner;
            }

        public boolean containsMutableFields()
            {
            for (ObjectHandle field : m_aFields)
                {
                if (field != null && field.isMutable())
                    {
                    return true;
                    }
                }
            return false;
            }

        @Override
        public boolean isService()
            {
            if (getComposition().getFieldNids().contains(OUTER))
                {
                ObjectHandle hParent = getField(OUTER);
                return hParent != null && hParent.isService();
                }

            return false;
            }

        @Override
        public ServiceHandle getService()
            {
            GenericHandle hParent = (GenericHandle) getField(OUTER);
            return hParent == null || !hParent.isService()
                ? null
                : hParent.getService();
            }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz)
            {
            // when we clone a struct into a non-struct, we need to update the inflated
            // RefHandles to point to a non-struct parent handle;
            // when we clone a non-struct to a struct, we need to do the opposite
            boolean fUpdateOuter = isStruct() || clazz.isStruct();

            GenericHandle  hClone  = (GenericHandle) super.cloneAs(clazz);
            ObjectHandle[] aFields = m_aFields;

            if (fUpdateOuter && aFields != null)
                {
                for (Object nid : clazz.getFieldNids())
                    {
                    if (clazz.isInflated(nid))
                        {
                        RefHandle    hValue = (RefHandle) clazz.getFieldFromStructure(aFields, nid);
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
            ObjectHandle[] aFields = m_aFields;
            if (aFields != null)
                {
                TypeComposition clazz = getComposition();
                for (Object idProp : clazz.getFieldNids())
                    {
                    ObjectHandle hValue = clazz.getFieldFromStructure(aFields, idProp);
                    if (hValue == null)
                        {
                        if (!clazz.isAllowedUnassigned(idProp))
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
                hClone.setOwner(owner);
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

        @Override
        protected boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            if (!getType().isShared(poolThat))
                {
                return false;
                }

            // TODO GG: we could make this check less expensive by pre-emptively calculating
            //          whether all the fields belong to the same pool, which would be a wast
            //          majority of the objects; in that case, no object tree traversing would be
            //          necessary
            if (mapVisited == null)
                {
                mapVisited = new IdentityHashMap<>();
                }

            return mapVisited.put(this, Boolean.TRUE) != null ||
                   areShared(m_aFields, poolThat, mapVisited);
            }

        public static boolean compareIdentity(GenericHandle h1, GenericHandle h2)
            {
            if (h1 == h2)
                {
                return true;
                }

            if (h1.isMutable() != h2.isMutable())
                {
                return false;
                }

            ObjectHandle[] aField1 = h1.getFields();
            ObjectHandle[] aField2 = h2.getFields();

            if (aField1 == aField2)
                {
                return true;
                }

            TypeComposition comp1 = h1.getComposition();
            TypeComposition comp2 = h2.getComposition();
            Set<Object> nids1 = comp1.getFieldNids();
            Set<Object> nids2 = comp2.getFieldNids();

            if (!nids1.equals(nids2))
                {
                return false;
                }

            for (Object nid : nids1)
                {
                ObjectHandle hV1 = comp1.getFieldFromStructure(aField1, nid);
                ObjectHandle hV2 = comp2.getFieldFromStructure(aField2, nid);

                // TODO: need to prevent a potential infinite loop
                ClassTemplate template = hV1.getTemplate();
                if (template != hV2.getTemplate() || !template.compareIdentity(hV1, hV2))
                    {
                    return false;
                    }
                }

            return true;
            }

        /**
         * The array of field values indexed according to the ClassComposition's field layout.
         */
        private final ObjectHandle[] m_aFields;

        /**
         * The owner field is most commonly not set, unless this object is a service, a module,
         * was injected or explicitly "masked as".
         */
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

    /**
     * A handle for any object that fits in a long.
     */
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

    /**
     * Abstract array handle.
     */
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
        abstract public void clear();

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

    /**
     * Native handle that holds a reference to a Constant from the ConstantPool.
     */
    public static class ConstantHandle
            extends ObjectHandle
        {
        public ConstantHandle(Constant constant)
            {
            super(xObject.CLASS);

            assert constant != null;
            f_constant = constant;
            }

        public Constant getConstant()
            {
            return f_constant;
            }

        @Override
        public String toString()
            {
            return f_constant.toString();
            }

        private final Constant f_constant;
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
        public boolean isPassThrough(Container container)
            {
            throw new IllegalStateException();
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
        private final TypeComposition f_clzArray;
        private final ObjectHandle[]  f_ahValue;

        public DeferredArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue)
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
            return "Deferred array initialization: " + getType();
            }
        }

    /**
     * DeferredSingletonHandle represents a deferred bully bound function call that puts a result
     * on the calling frame's stack.
     *
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredFunctionCall
            extends DeferredCallHandle
        {
        private final FullyBoundHandle f_hf;

        public DeferredFunctionCall(FullyBoundHandle hf)
            {
            super((ExceptionHandle) null);

            f_hf = hf;
            }

        @Override
        public void addContinuation(Frame.Continuation continuation)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation)
            {
            switch (f_hf.call1(frameCaller, null, Utils.OBJECTS_NONE, Op.A_STACK))
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
            return "Deferred function call for " + f_hf;
            }
        }

    /**
     * A handle that is used during circular singleton initialization process.
     */
    public static class InitializingHandle
            extends ObjectHandle
        {
        private final SingletonConstant f_constSingleton;

        public InitializingHandle(SingletonConstant constSingleton)
            {
            super(null);

            f_constSingleton = constSingleton;
            }

        /**
         * @return the underlying initialized object or null
         */
        public ObjectHandle getInitialized()
            {
            ObjectHandle hConst = f_constSingleton.getHandle();
            return hConst == this ? null : hConst;
            }

        /**
         * @return the underlying initialized object
         * @throws IllegalStateException if the underlying object is not yet initialized
         */
        protected ObjectHandle assertInitialized()
            {
            ObjectHandle hConst = f_constSingleton.getHandle();
            if (hConst instanceof InitializingHandle)
                {
                throw new IllegalStateException("Circular initialization");
                }
            return hConst;
            }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz)
            {
            return assertInitialized().cloneAs(clazz);
            }

        @Override
        public ObjectHandle revealOrigin()
            {
            return assertInitialized().revealOrigin();
            }

        @Override
        public List<String> validateFields()
            {
            return assertInitialized().validateFields();
            }

        @Override
        public boolean isSelfContained()
            {
            return assertInitialized().isSelfContained();
            }

        @Override
        public TypeComposition getComposition()
            {
            return assertInitialized().getComposition();
            }

        @Override
        public boolean isPassThrough(Container container)
            {
            return assertInitialized().isPassThrough(container);
            }

        @Override
        public boolean isService()
            {
            return assertInitialized().isService();
            }

        @Override
        public ServiceHandle getService()
            {
            return assertInitialized().getService();
            }

        @Override
        public boolean isNativeEqual()
            {
            return assertInitialized().isNativeEqual();
            }

        @Override
        public ObjectHandle maskAs(Container owner, TypeConstant typeAs)
            {
            return assertInitialized().maskAs(owner, typeAs);
            }

        @Override
        public ObjectHandle revealAs(Frame frame, TypeConstant typeAs)
            {
            return assertInitialized().revealAs(frame, typeAs);
            }

        @Override
        protected boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            return assertInitialized().isShared(poolThat, mapVisited);
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            return assertInitialized().compareTo(that);
            }

        @Override
        public int hashCode()
            {
            return assertInitialized().hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            return assertInitialized().equals(obj);
            }

        @Override
        public String toString()
            {
            ObjectHandle hConst = getInitialized();
            return hConst == null ? "<initializing>" : hConst.toString();
            }
        };

    /**
     * A handle that is used as an indicator for a default method argument value.
     */
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


    // ----- DEFERRED ------------------------------------------------------------------------------

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
