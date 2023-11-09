package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition.FieldInfo;

import org.xvm.runtime.template.Proxy;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xChar;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Handy;


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
    protected boolean m_fMutable;

    protected ObjectHandle(TypeComposition clazz)
        {
        m_clazz    = clazz;
        m_fMutable = false;
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

    /**
     * Mark the object as immutable.
     *
     * @return true if the object has been successfully marked as immutable; false otherwise
     */
    public boolean makeImmutable()
        {
        m_fMutable = false;
        return true;
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
        if (!isMutable())
            {
            type = type.freeze();
            }
        if (isService())
            {
            type = type.ensureService();
            }
        return type;
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
     * @return true iff the specified property has custom code or is Ref-annotated
     */
    public boolean isInflated(PropertyConstant idProp)
        {
        FieldInfo field = getComposition().getFieldInfo(idProp);
        return field != null && field.isInflated();
        }

    /**
     * @return true iff the specified property has an injected value
     */
    public boolean isInjected(PropertyConstant idProp)
        {
        return getComposition().isInjected(idProp);
        }

    /**
     * @return true iff the specified property has an atomic value
     */
    public boolean isAtomic(PropertyConstant idProp)
        {
        return getComposition().isAtomic(idProp);
        }

    /**
     * @return true iff the handle is an object that is allowed to be passed across service
     *         boundaries within the same container (an immutable, a service or an object that has
     *         all pass-through fields)
     */
    public boolean isPassThrough()
        {
        return isPassThrough(null);
        }

    /**
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

        return isShared(container.getConstantPool(), null);
        }

    /**
     * Check if this immutable handle belongs to the same type system as the one represented by the
     * specified ConstantPool.
     *
     * @param poolThat    the pool representing the "receiving" container
     * @param mapVisited  the identity hash map of visited objects
     *
     * @return true iff this object's type is shared with that pool
     */
    public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
        {
        return true;
        }

    /**
     * Helper method to check if all the immutable specified handles belongs to the same type system
     * as the one represented by the specified ConstantPool.
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
     * @return a ServiceHandle or null of this handle is "not a Service"
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
        TypeComposition clz = getComposition();

        // don't add "immutable" for immutable types
        return "(" + (m_fMutable || clz.getType().isImmutable() ? "" : "immutable ") + clz + ") ";
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


        // ----- id-based field access -------------------------------------------------------------

        public boolean containsField(PropertyConstant idProp)
            {
            return getComposition().getFieldInfo(idProp) != null;
            }

        public ObjectHandle getField(Frame frame, PropertyConstant idProp)
            {
            FieldInfo field = getComposition().getFieldInfo(idProp);
            return field.isTransient()
                    ? getTransientField(frame, field)
                    : m_aFields[field.getIndex()];
            }

        public ObjectHandle getField(Frame frame, String sProp)
            {
            FieldInfo field = getComposition().getFieldInfo(sProp);
            return field.isTransient()
                    ? getTransientField(frame, field)
                    : m_aFields[field.getIndex()];
            }

        public void setField(Frame frame, PropertyConstant idProp, ObjectHandle hValue)
            {
            FieldInfo field = getComposition().getFieldInfo(idProp);
            if (field.isTransient())
                {
                setTransientField(frame, field.getIndex(), hValue);
                }
            else
                {
                m_aFields[field.getIndex()] = hValue;
                }
            }

        public void setField(Frame frame, String sProp, ObjectHandle hValue)
            {
            FieldInfo field = getComposition().getFieldInfo(sProp);
            if (field.isTransient())
                {
                setTransientField(frame, field.getIndex(), hValue);
                }
            else
                {
                m_aFields[field.getIndex()] = hValue;
                }
            }

        public FieldInfo getFieldInfo(PropertyConstant idProp)
            {
            return getComposition().getFieldInfo(idProp);
            }

        // ----- index-based field access ----------------------------------------------------------

        public ObjectHandle getField(int iPos)
            {
            return m_aFields[iPos];
            }

        public void setField(int iPos, ObjectHandle hValue)
            {
            m_aFields[iPos] = hValue;
            }

        public ObjectHandle getTransientField(Frame frame, FieldInfo field)
            {
            TransientId  hId    = (TransientId) m_aFields[field.getIndex()];
            ObjectHandle hValue = frame.f_context.getTransientValue(hId);

            if (hValue == null && field.isInflated())
                {
                RefHandle hRef = field.createRefHandle(frame);
                hRef.setField(frame, OUTER, this);
                frame.f_context.setTransientValue(hId, hRef);
                return hRef;
                }
            return hValue;
            }

        public void setTransientField(Frame frame, int iPos, ObjectHandle hValue)
            {
            frame.f_context.setTransientValue((TransientId) m_aFields[iPos], hValue);
            }

        public Container getOwner()
            {
            return m_owner == null ? getComposition().getContainer() : m_owner;
            }

        public void setOwner(Container owner)
            {
            m_owner = owner;
            }

        public boolean containsMutableFields()
            {
            for (ObjectHandle hField : m_aFields)
                {
                if (hField != null && hField.isMutable())
                    {
                    return true;
                    }
                }
            return false;
            }

        @Override
        public boolean isService()
            {
            if (m_fMutable && getComposition().isInstanceChild())
                {
                ObjectHandle hParent = getField(null, OUTER);
                return hParent != null && hParent.isService();
                }

            return false;
            }

        @Override
        public ServiceHandle getService()
            {
            ObjectHandle hParent = getField(null, OUTER);
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
                for (FieldInfo field : clazz.getFieldLayout().values())
                    {
                    if (field.isInflated() && !field.isTransient())
                        {
                        RefHandle    hValue = (RefHandle) aFields[field.getIndex()];
                        ObjectHandle hOuter = hValue.getField(null, OUTER);
                        if (hOuter != null)
                            {
                            hValue.setField(null, OUTER, hClone);
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
                for (FieldInfo field : clazz.getFieldLayout().values())
                    {
                    ObjectHandle hValue = aFields[field.getIndex()];
                    if (hValue == null)
                        {
                        if (!field.isAllowedUnassigned())
                            {
                            if (listUnassigned == null)
                                {
                                listUnassigned = new ArrayList<>();
                                }
                            listUnassigned.add(field.getName());
                            }
                        }
                    // no need to recurse to a field; it would throw during its own construction
                    }
                }
            return listUnassigned;
            }

        @Override
        public boolean makeImmutable()
            {
            if (m_fMutable)
                {
                // mark ourselves as immutable to prevent an infinite recursion
                m_fMutable = false;
                if (getComposition().makeStructureImmutable(m_aFields))
                    {
                    return true;
                    }
                // the structure could not be made mutable
                m_fMutable = true;
                return false;
                }
            return true;
            }

        @Override
        public boolean isNativeEqual()
            {
            return false;
            }

        @Override
        public GenericHandle maskAs(Container owner, TypeConstant typeAs)
            {
            if (!isService())
                {
                TypeConstant type = getType();
                assert type.isImmutable() && type.isSingleUnderlyingClass(true);

                ModuleConstant idModule = type.getSingleUnderlyingClass(true).getModuleConstant();
                if (!idModule.isCoreModule())
                    {
                    // even though it's a const, all calls need to be proxied
                    ProxyComposition clzProxy = new ProxyComposition(
                            (ClassComposition) getComposition(), typeAs);
                    return Proxy.makeHandle(clzProxy, owner.getServiceContext(), this);
                    }
                }

            Container       ownerOrig = getOwner();
            TypeComposition clzAs;
            if (owner == ownerOrig || typeAs.isShared(ownerOrig.getConstantPool()))
                {
                clzAs = getComposition().maskAs(typeAs);
                }
            else
                {
                // the ownership is moved to a different container that is not shared with the
                // current owner's container; ensure the class composition at the new owner
                ClassComposition clz           = (ClassComposition) getComposition();
                TypeConstant     typeInception = clz.getInceptionType().removeAccess();
                assert typeInception.isShared(owner.getConstantPool());

                typeInception = (TypeConstant) owner.getConstantPool().register(typeInception);
                clzAs = owner.ensureClassComposition(typeInception, clz.getTemplate()).maskAs(typeAs);
                }

            if (clzAs != null)
                {
                GenericHandle hClone = (GenericHandle) cloneAs(clzAs);
                hClone.setOwner(owner);
                return hClone;
                }
            return null;
            }

        @Override
        public GenericHandle revealAs(Frame frame, TypeConstant typeAs)
            {
            Container owner = m_owner;
            if (owner != null)
                {
                // only the owner or its parent(s) can reveal
                Container caller = frame.f_context.f_container;
                if (caller != owner && !caller.isParent(owner))
                    {
                    return null;
                    }
                }

            TypeComposition clzAs = getComposition().revealAs(typeAs);
            return clzAs == null
                    ? null
                    : (GenericHandle) cloneAs(clzAs);
            }

        @Override
        public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            TypeConstant type = getType();
            if (!type.isShared(poolThat))
                {
                return false;
                }

            if (isService())
                {
                return true;
                }

            if (mapVisited == null)
                {
                mapVisited = new IdentityHashMap<>();
                }

            if (mapVisited.put(this, Boolean.TRUE) != null ||
                    areShared(m_aFields, poolThat, mapVisited))
                {
                return true;
                }
            return false;
            }

        /**
         * The array of field values indexed according to the ClassComposition's field layout.
         */
        private final ObjectHandle[] m_aFields;

        /**
         * The "m_owner" field is most commonly not set, unless this object is a service, a module,
         * was injected or explicitly "masked as".
         */
        protected Container m_owner;

        /**
         * Synthetic property holding a reference to a parent instance.
         */
        public static final String OUTER = "$outer";
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

        @Override
        public String toString()
            {
            ObjectHandle hText = getField(null, "text");
            return super.toString() +
                (hText instanceof StringHandle hString
                    ? Handy.quotedString(hString.getStringValue())
                    : "");
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
            return obj instanceof JavaLong that && m_lValue == that.m_lValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + (m_clazz.getTemplate() == xChar.INSTANCE
                    ? Handy.quotedChar((char) m_lValue)
                    : String.valueOf(m_lValue));
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
                if (continuation != null)
                    {
                    frameNext.addContinuation(continuation);
                    }
                return frameCaller.call(frameNext);
                }

            return frameCaller.raiseException(f_hException);
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
                    xArray.createImmutableArray(f_clzArray, f_ahValue));

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
     * A handle that is used for transient fields access.
     */
    public static class TransientId
            extends ObjectHandle
        {
        protected TransientId()
            {
            super(null);

            f_nHash = s_hashCode.getAndAdd(0x61c88647); // see ThreadLocal.java
            }

        @Override
        public int hashCode()
            {
            return f_nHash;
            }

        private final int f_nHash;

        private static final AtomicInteger s_hashCode = new AtomicInteger();
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
                throw new IllegalStateException("Circular initialization \"" +
                        f_constSingleton.getValue().getValueString() + '"');
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
        public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
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
        }

    /**
     * A handle that is used for blocking IO operations.
     */
    public static class NativeFutureHandle
            extends ObjectHandle
        {
        protected NativeFutureHandle(CompletableFuture cf)
            {
            super(null);

            f_future = cf;
            }

        @Override
        public String toString()
            {
            return "Native: " + f_future;
            }

        public final CompletableFuture f_future;
        }
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
    }