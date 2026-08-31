package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.Nid;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.ClassComposition.FieldInfo;

import org.xvm.runtime.template.Proxy;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xChar;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Handy;
import static org.xvm.util.Handy.copyOf;


/**
 * Runtime operates on Object handles holding the struct references or the values themselves
 * for the following types:
 *  Bit, Boolean, Char, Int, UInt, Nullable.Null, and optionally for some Tuples
 * <p>
 * Note, that the equals() and hashCode() methods should be only for immutable handles.
 */
public abstract class ObjectHandle
        implements Cloneable {
    /**
     * The composition (class, access view, and type) of this handle. Written only at
     * construction and onto the fresh clone inside {@link #cloneAs}; every other reader goes
     * through {@link #getComposition()}, so the write discipline is compiler-enforced.
     */
    private TypeComposition m_clazz;

    /**
     * The mutability flag. Direct writes are permitted ONLY in constructors, where views cannot
     * exist yet; every post-construction access must go through {@link #isMutable()} and
     * {@link #setMutable(boolean)}, so GenericHandle can reroute the state into a cell shared by
     * all cloneAs views. Direct field writes in subclass makeImmutable() overrides were exactly
     * how a freeze through one view left sibling views claiming mutability. (Constructor writes
     * stay direct rather than calling a final initializer because javac's conservative
     * this-escape analysis, fatal under this build's gate, flags even final-method calls from
     * subclass constructors.)
     */
    protected boolean m_fMutable;

    protected ObjectHandle(TypeComposition clazz) {
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
    public ObjectHandle cloneAs(TypeComposition clazz) {
        if (isMutable() && !supportsMutableViews()) {
            // default-deny (clone-eradication study): a shallow view copy of a handle whose
            // lifecycle is still live splits per-view state - m_fMutable at minimum - from the
            // storage every view shares; that is the entire freeze-split bug family. Classes
            // whose views share all lifecycle state opt in via supportsMutableViews() (see
            // GenericHandle's freeze/init/referent cells, ArrayHandle's ArrayState, and the
            // base freeze cell TupleHandle/FunctionHandle share); everything else stays
            // refused until it earns the opt-in, so an unreviewed future handle class fails
            // loudly here instead of desyncing silently.
            throw new IllegalStateException("mutable handle cannot be cloned as a view: " + this);
        }
        if (supportsMutableViews()) {
            // the freeze state must be shared before the shallow copy below duplicates it
            prepareMutableViewShare();
        }
        try {
            ObjectHandle handle = (ObjectHandle) super.clone();
            handle.m_clazz = clazz;
            return handle;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Share the lifecycle state that must be common to every view BEFORE a shallow view copy is
     * taken. The default installs the shared freeze cell; a subclass whose lifecycle state
     * already lives in its own constructor-final shared cell (ArrayHandle's ArrayState)
     * overrides this to skip the redundant cell.
     */
    protected void prepareMutableViewShare() {
        ensureSharedFreeze();
    }

    /**
     * The freeze state shared by every cloneAs view of this object, installed lazily by the
     * first view clone. The per-instance flag is a shallow-copied field, but views are views
     * over shared storage: on the per-view shape, makeImmutable() through one view left
     * sibling views claiming mutability and therefore willing to write into the frozen shared
     * storage. Handles that never have views never pay for the cell, and the CAS install
     * keeps a racing pair of first clones from forking two cells.
     */
    private volatile FreezeCell m_cellFreeze;

    private static final AtomicReferenceFieldUpdater<ObjectHandle, FreezeCell> FREEZE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    ObjectHandle.class, FreezeCell.class, "m_cellFreeze");

    private static final class FreezeCell {
        volatile boolean mutable;

        FreezeCell(boolean fMutable) {
            mutable = fMutable;
        }
    }

    protected final void ensureSharedFreeze() {
        if (m_cellFreeze == null) {
            FREEZE_UPDATER.compareAndSet(this, null, new FreezeCell(m_fMutable));
        }
    }

    /**
     * @return true iff every view of this handle shares all of its lifecycle state, making a
     *         mutable view clone safe; the default is false, so mutable handles refuse
     *         {@link #cloneAs} until their class explicitly earns the opt-in
     */
    protected boolean supportsMutableViews() {
        return false;
    }

    /**
     * Reveal this handle using the "inception" type.
     *
     * @return the "fully accessible" handle
     */
    public ObjectHandle revealOrigin() {
        return getComposition().ensureOrigin(this);
    }

    public boolean isMutable() {
        FreezeCell cell = m_cellFreeze;
        return cell == null ? m_fMutable : cell.mutable;
    }

    /**
     * Post-construction mutability transition, routed into the freeze cell once views share it;
     * overridable so a subclass with its own shared lifecycle cell can reroute further.
     */
    protected void setMutable(boolean fMutable) {
        FreezeCell cell = m_cellFreeze;
        if (cell == null) {
            m_fMutable = fMutable;
        } else {
            cell.mutable = fMutable;
        }
    }

    /**
     * Mark the object as immutable.
     *
     * @return true if the object has been successfully marked as immutable; false otherwise
     */
    public boolean makeImmutable() {
        setMutable(false);
        return true;
    }

    /**
     * @return null iff all the fields are assigned; a list of unassigned names otherwise
     */
    public List<String> validateFields() {
        return null;
    }

    public boolean isSelfContained() {
        return false;
    }

    /**
     * @return the TypeComposition for this handle
     */
    public TypeComposition getComposition() {
        return m_clazz;
    }

    // ----- native dispatch -----------------------------------------------------------------------

    /*
     * Dispatch of a native method belongs on the RECEIVER, not on an object the receiver hands the
     * call to. Today every one of these arrives at the template as
     *
     *     hTarget.getTemplate().invokeNative1(frame, method, hTarget, hArg, iReturn)
     *
     * - the handle produces the template and then passes ITSELF back as a parameter - and the
     * template's first act is to cast that parameter back to the type it already was. These four
     * defaults reproduce exactly that call, so nothing changes until a handle class overrides one;
     * a handle that does gets `this` already correctly typed, with no cast and no type parameter.
     *
     * Deliberately only these four. 107 methods take an `ObjectHandle hTarget`, and giving all of
     * them a default here would drag the whole ClassTemplate API onto this class. The line is that
     * DISPATCH belongs on the receiver while OPERATIONS - hashing, rendering, arithmetic, field
     * access - belong on the template, which is why buildHashCode and invokeAdd are not here.
     */

    /**
     * Invoke a native method with a single argument on this handle.
     *
     * @param frame    the current frame
     * @param method   the method to invoke
     * @param hArg     the argument
     * @param iReturn  the register to place the result into
     *
     * @return one of the {@code Op.R_*} values
     */
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hArg, int iReturn) {
        return getTemplate().invokeNative1(frame, method, this, hArg, iReturn);
    }

    /**
     * Invoke a native method with any number of arguments and one return on this handle.
     *
     * @param frame    the current frame
     * @param method   the method to invoke
     * @param ahArg    the arguments
     * @param iReturn  the register to place the result into
     *
     * @return one of the {@code Op.R_*} values
     */
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle[] ahArg, int iReturn) {
        return getTemplate().invokeNativeN(frame, method, this, ahArg, iReturn);
    }

    /**
     * Invoke a native method with any number of arguments and returns on this handle.
     *
     * @param frame     the current frame
     * @param method    the method to invoke
     * @param ahArg     the arguments
     * @param aiReturn  the registers to place the results into
     *
     * @return one of the {@code Op.R_*} values
     */
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle[] ahArg,
                              int[] aiReturn) {
        return getTemplate().invokeNativeNN(frame, method, this, ahArg, aiReturn);
    }

    /**
     * Read a native property from this handle.
     *
     * @param frame      the current frame
     * @param sPropName  the property name
     * @param iReturn    the register to place the result into
     *
     * @return one of the {@code Op.R_*} values
     */
    public int invokeNativeGet(Frame frame, String sPropName, int iReturn) {
        return getTemplate().invokeNativeGet(frame, sPropName, this, iReturn);
    }


    /**
     * @return the underlying template for this handle
     */
    public ClassTemplate getTemplate() {
        return getComposition().getTemplate();
    }

    /**
     * @return the underlying template for this handle as the expected type
     */
    public <T extends ClassTemplate> T getTemplate(Class<T> clzTemplate) {
        return clzTemplate.cast(getComposition().getTemplate());
    }

    /**
     * @return the OpSupport for the inception type of this handle
     */
    public OpSupport getOpSupport() {
        return getComposition().getSupport();
    }

    /**
     * @return the revealed type of this handle
     */
    public TypeConstant getType() {
        return augmentType(getComposition().getType());
    }

    /**
     * Augment the type based on the handle immutability and serviceability.
     */
    protected TypeConstant augmentType(TypeConstant type) {
        if (!isMutable()) {
            type = type.freeze();
        }
        if (isService()) {
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
    public TypeConstant getUnsafeType() {
        return getType();
    }

    public ObjectHandle ensureAccess(Constants.Access access) {
        return getComposition().ensureAccess(this, access);
    }

    /**
     * @return true iff the specified property has custom code or is Ref-annotated
     */
    public boolean isInflated(PropertyConstant idProp) {
        FieldInfo field = getComposition().getFieldInfo(idProp);
        return field != null && field.isInflated();
    }

    /**
     * @return true iff the specified property has an injected value
     */
    public boolean isInjected(PropertyConstant idProp) {
        return getComposition().isInjected(idProp);
    }

    /**
     * @return true iff the specified property has an atomic value
     */
    public boolean isAtomic(PropertyConstant idProp) {
        return getComposition().isAtomic(idProp);
    }

    /**
     * @return true iff the handle is an object that is allowed to be passed across service
     *         boundaries within the same container (an immutable, a service or an object that has
     *         all pass-through fields)
     */
    public boolean isPassThrough() {
        return isPassThrough(null);
    }

    /**
     * @return true iff the handle is an object that is allowed to be passed across service/container
     *         boundaries (an immutable, a service or an object that has all pass-through fields)
     */
    public boolean isPassThrough(Container container) {
        if (isService()) {
            return true;
        }

        if (isMutable()) {
            return false;
        }

        if (container == null) {
            return true;
        }

        return isShared(container, null);
    }

    /**
     * Check if this immutable handle belongs to the same type system as the one represented by the
     * specified ConstantPool.
     *
     * @param container   the "receiving" container
     * @param mapVisited  the identity hash map of visited objects
     *
     * @return true iff this object's type is shared with that pool
     */
    public boolean isShared(Container container, Map<ObjectHandle, Boolean> mapVisited) {
        return true;
    }

    /**
     * Helper method to check if all the immutable specified handles belongs to the same type system
     * as the one represented by the specified ConstantPool.
     *
     * @param ahValue     an array of handles to check
     * @param container   the "receiving" container
     * @param mapVisited  the identity hash map of visited objects
     *
     * @return true iff this object's type is shared with that pool
     */
    protected static boolean areShared(ObjectHandle[] ahValue, Container container,
                                       Map<ObjectHandle, Boolean> mapVisited) {
        for (ObjectHandle field : ahValue) {
            if (field != null && !field.isShared(container, mapVisited)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true iff the handle is a non-constant object for which all method invocations
     *         and properties access need to be proxied across service boundaries
     */
    public boolean isService() {
        return false;
    }

    /**
     * If method invocations and properties access for this handle need to be proxied across
     * service boundaries, return the corresponding ServiceHandle.
     *
     * @return a ServiceHandle or null of this handle is "not a Service"
     */
    public ServiceHandle getService() {
        return null;
    }

    /**
     * @return true iff the handle represents a struct
     */
    public boolean isStruct() {
        return getComposition().isStruct();
    }

    /**
     * @return true iff the handle itself could be used for the equality check
     */
    public boolean isNativeEqual() {
        return true;
    }

    /**
     * Mask this handle to the specified type on behalf of the specified container.
     *
     * @return a new handle for this object masked to the specified type or null if the
     *         request cannot be fulfilled
     */
    public ObjectHandle maskAs(Container owner, TypeConstant typeAs) {
        return this;
    }

    /**
     * Reveal this handle as the specified type on the context of the specified frame.
     *
     * @return a new handle for this object revealed as the specified type or null if the
     *         request cannot be fulfilled
     */
    public ObjectHandle revealAs(Frame frame, TypeConstant typeAs) {
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
    public int proceed(Frame frameCaller, Frame.Continuation continuation) {
        throw new IllegalStateException("Not deferred");
    }

    /**
     * @return the result of comparison (only for isNativeEqual() handles)
     */
    public int compareTo(ObjectHandle that) {
        throw new UnsupportedOperationException(getClass() + " cannot compare");
    }

    @Override
    public int hashCode() {
        if (isNativeEqual()) {
            throw new UnsupportedOperationException(getClass() + " must implement \"hashCode()\"");
        }

        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (isNativeEqual()) {
            throw new UnsupportedOperationException(getClass() + " must implement \"equals()\"");
        }

        // we don't use this for natural equality check
        return this == obj;
    }

    @Override
    public String toString() {
        // PURE: mutability from the handle's own flag only. The former "|| clz.getType().isImmutable()"
        // resolved getType() and recursed through isImmutable() for relational types; drop it. The
        // composition label is pure now that ParameterizedTypeConstant.getValueString renders
        // structurally. See docs/reentrancy/plans/side-effect-free-tostring.md.
        return "(" + (isMutable() ? "" : "immutable ") + getComposition() + ") ";
    }

    public static class GenericHandle
            extends ObjectHandle {
        public GenericHandle(TypeComposition clazz) {
            super(clazz);

            m_fMutable = true;

            m_aFields = clazz.initializeStructure();
        }

        public ObjectHandle[] getFields() {
            return fieldView();
        }


        // ----- id-based field access -------------------------------------------------------------

        public boolean containsField(PropertyConstant idProp) {
            return getComposition().getFieldInfo(idProp) != null;
        }

        public ObjectHandle getField(Frame frame, PropertyConstant idProp) {
            FieldInfo field = getComposition().getFieldInfo(idProp);

            return field == null
                    ? missingPropertyException(frame, idProp.getName())
                    : getField(frame, field);
        }

        public ObjectHandle getField(Frame frame, String sProp) {
            FieldInfo field = getComposition().getFieldInfo(Nid.of(sProp));

            return field == null
                    ? missingPropertyException(frame, sProp)
                    : getField(frame, field);
        }

        /**
         * Non-forcing field read for DISPLAY only: returns the named field's value iff this handle's
         * composition field layout is ALREADY computed, else null. Never forces the layout Lazy cell
         * (getField(...) would, via getFieldInfo -> fieldLayout()) - a debugger, or Throwable.toString
         * rendering an exception, must not force it. See
         * docs/reentrancy/plans/side-effect-free-tostring.md.
         */
        protected ObjectHandle peekField(String sName) {
            return getComposition() instanceof ClassComposition clz && clz.isFieldLayoutComputed()
                    ? getField(null, sName)
                    : null;
        }

        private ObjectHandle missingPropertyException(Frame frame, String sProp) {
            return new DeferredCallHandle(
                    xException.makeHandle(frame, "Missing property: " + sProp));
        }

        public ObjectHandle getField(Frame frame, FieldInfo field) {
            return field.isTransient()
                    ? getTransientField(frame, field)
                    : fieldValue(field.getIndex());
        }

        public void setField(Frame frame, PropertyConstant idProp, ObjectHandle hValue) {
            FieldInfo field = getComposition().getFieldInfo(idProp);
            if (field.isTransient()) {
                setTransientField(frame, field.getIndex(), hValue);
            } else {
                setFieldValue(field.getIndex(), hValue);
            }
        }

        public void setField(Frame frame, String sProp, ObjectHandle hValue) {
            FieldInfo field = getComposition().getFieldInfo(Nid.of(sProp));
            if (field.isTransient()) {
                setTransientField(frame, field.getIndex(), hValue);
            } else {
                setFieldValue(field.getIndex(), hValue);
            }
        }

        public FieldInfo getFieldInfo(PropertyConstant idProp) {
            return getComposition().getFieldInfo(idProp);
        }

        // ----- index-based field access ----------------------------------------------------------

        public ObjectHandle getField(int iPos) {
            return fieldValue(iPos);
        }

        public void setField(int iPos, ObjectHandle hValue) {
            setFieldValue(iPos, hValue);
        }

        /**
         * Initialize a regular field from a handle constructor without dispatching through the
         * public field-access API. Constructor-time field writes must not call overridable/public
         * methods that could observe a partially constructed handle.
         */
        protected final void initializeField(String sProp, ObjectHandle hValue) {
            FieldInfo field = getComposition().getFieldInfo(Nid.of(sProp));
            if (field == null || field.isTransient()) {
                throw new IllegalStateException("Cannot initialize field: " + sProp);
            }
            m_aFields[field.getIndex()] = hValue;
        }

        public ObjectHandle getTransientField(Frame frame, FieldInfo field) {
            TransientId  hId    = (TransientId) m_aFields[field.getIndex()];
            ObjectHandle hValue = frame.f_context.getTransientValue(hId);

            if (hValue == null && field.isInflated()) {
                RefHandle hRef = field.createRefHandle(frame);
                hRef.setField(frame, OUTER, this);
                frame.f_context.setTransientValue(hId, hRef);
                return hRef;
            }
            return hValue;
        }

        public void setTransientField(Frame frame, int iPos, ObjectHandle hValue) {
            frame.f_context.setTransientValue((TransientId) m_aFields[iPos], hValue);
        }

        public Container getOwner() {
            return m_owner == null ? getComposition().getContainer() : m_owner;
        }

        public void setOwner(Container owner) {
            m_owner = owner;
        }

        public boolean containsMutableFields() {
            for (ObjectHandle hField : fieldView()) {
                if (hField != null && hField.isMutable()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isService() {
            if (isMutable() && getComposition().isInstanceChild()) {
                ObjectHandle hParent = getField(null, OUTER);
                return hParent != null && hParent.isService();
            }

            return false;
        }

        @Override
        public ServiceHandle getService() {
            ObjectHandle hParent = getField(null, OUTER);
            return hParent == null || !hParent.isService()
                ? null
                : hParent.getService();
        }

        @Override
        protected boolean supportsMutableViews() {
            // GenericHandle views share their lifecycle state: the freeze cell, the lazy-init
            // guard, the atomic/injected referent cells, and the copy-on-write field-override
            // layer together make mutable access views (struct <-> public during construction,
            // reveal/mask) safe - this is the one designed-in mutable-view family
            return true;
        }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz) {
            // cloneAs() creates another access view of the same object. The field array therefore
            // remains shared for regular values, but inflated RefHandles need a view-local $outer:
            // mutating the shared RefHandle would make the source view suddenly point at the new
            // view, which breaks revealed/struct view semantics under single-threaded use and
            // becomes a race when multiple containers or fibers reveal/mask the same object.
            boolean fUpdateOuter = isStruct() || clazz.isStruct();

            GenericHandle hClone = (GenericHandle) super.cloneAs(clazz);
            hClone.m_aFieldOverrides = m_aFieldOverrides;

            if (fUpdateOuter) {
                for (FieldInfo field : clazz.getFieldLayout().values()) {
                    if (field.isInflated() && !field.isTransient()) {
                        ObjectHandle hValue = fieldValue(field.getIndex());
                        if (hValue instanceof RefHandle hRef &&
                                hRef.getField(null, OUTER) != null) {
                            RefHandle hRefClone = (RefHandle) hRef.cloneAs(hRef.getComposition());
                            ((GenericHandle) hRefClone).overrideField(OUTER, hClone);
                            hClone.overrideField(field.getIndex(), hRefClone);
                        }
                    }
                }
            }
            return hClone;
        }

        @Override
        public List<String> validateFields() {
            List<String> listUnassigned = null;
            ObjectHandle[] aFields = m_aFields;
            if (aFields != null) {
                TypeComposition clazz = getComposition();
                for (FieldInfo field : clazz.getFieldLayout().values()) {
                    ObjectHandle hValue = fieldValue(field.getIndex());
                    if (hValue == null) {
                        if (!field.isAllowedUnassigned()) {
                            if (listUnassigned == null) {
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
        public boolean makeImmutable() {
            if (isMutable()) {
                // mark ourselves as immutable to prevent an infinite recursion
                setMutable(false);
                if (getComposition().makeStructureImmutable(fieldView())) {
                    return true;
                }
                // the structure could not be made mutable
                setMutable(true);
                return false;
            }
            return true;
        }

        @Override
        public boolean isNativeEqual() {
            return false;
        }

        @Override
        public GenericHandle maskAs(Container owner, TypeConstant typeAs) {
            if (!isService()) {
                TypeConstant type = getType();
                assert type.isSingleUnderlyingClass(true);

                ModuleConstant idModule = type.getSingleUnderlyingClass(true).getModuleConstant();
                if (!idModule.isCoreModule()) {
                    // even though it's a const, all calls need to be proxied
                    ProxyComposition clzProxy = new ProxyComposition(getComposition(), typeAs);
                    return Proxy.makeHandle(clzProxy, owner.getServiceContext(), this, true);
                }
            }

            Container       ownerOrig = getOwner();
            TypeComposition clzAs;
            if (owner != ownerOrig && !isShared(owner, null)) {
                // cloneAs() creates another access view of the same runtime object; it is not an
                // ownership-transfer mechanism. A cross-owner mask is safe only when the whole
                // handle graph is already shared with the target container. Non-core objects use the
                // proxy path above instead of sharing the handle graph directly.
                return null;
            }

            if (owner == ownerOrig || typeAs.isShared(ownerOrig.getConstantPool())) {
                clzAs = getComposition().maskAs(typeAs);
            } else {
                // the ownership is moved to a different container that is not shared with the
                // current owner's container; ensure the class composition at the new owner
                ClassComposition clz           = (ClassComposition) getComposition();
                TypeConstant     typeInception = clz.getInceptionType().removeAccess();
                assert typeInception.isShared(owner.getConstantPool());

                typeInception = owner.getConstantPool().register(typeInception);
                clzAs = owner.ensureClassComposition(typeInception, clz.getTemplate()).maskAs(typeAs);
            }

            if (clzAs != null) {
                GenericHandle hClone = (GenericHandle) cloneAs(clzAs);
                hClone.setOwner(owner);
                return hClone;
            }
            return null;
        }

        @Override
        public GenericHandle revealAs(Frame frame, TypeConstant typeAs) {
            Container owner = m_owner;
            if (owner != null) {
                // only the owner or its parent(s) can reveal
                Container caller = frame.f_context.f_container;
                if (caller != owner && !caller.isParent(owner)) {
                    return null;
                }
            }

            TypeComposition clzAs = getComposition().revealAs(typeAs);
            return clzAs == null
                    ? null
                    : (GenericHandle) cloneAs(clzAs);
        }

        @Override
        public boolean isShared(Container container, Map<ObjectHandle, Boolean> mapVisited) {
            TypeConstant type = getType();
            if (!type.isShared(container.getConstantPool())) {
                return false;
            }

            if (isService()) {
                return true;
            }

            if (mapVisited == null) {
                mapVisited = new IdentityHashMap<>();
            }

            if (mapVisited.put(this, Boolean.TRUE) != null ||
                    areShared(fieldView(), container, mapVisited)) {
                return true;
            }
            return false;
        }

        ObjectHandle[] getFieldViewForDiagnostics() {
            return fieldView();
        }

        ObjectHandle[] getFieldOverridesForDiagnostics() {
            return m_aFieldOverrides;
        }

        private ObjectHandle fieldValue(int iPos) {
            ObjectHandle[] aOverrides = m_aFieldOverrides;
            ObjectHandle   hOverride  = aOverrides == null ? null : aOverrides[iPos];
            return hOverride == null ? m_aFields[iPos] : hOverride;
        }

        private void setFieldValue(int iPos, ObjectHandle hValue) {
            if (hasFieldOverride(iPos)) {
                overrideField(iPos, hValue);
            } else {
                m_aFields[iPos] = hValue;
            }
        }

        private boolean hasFieldOverride(int iPos) {
            ObjectHandle[] aOverrides = m_aFieldOverrides;
            return aOverrides != null && aOverrides[iPos] != null;
        }

        private ObjectHandle[] fieldView() {
            ObjectHandle[] aOverrides = m_aFieldOverrides;
            if (aOverrides == null) {
                return m_aFields;
            }

            ObjectHandle[] aView = copyOf(m_aFields);
            for (int i = 0, c = aView.length; i < c; ++i) {
                ObjectHandle hOverride = aOverrides[i];
                if (hOverride != null) {
                    aView[i] = hOverride;
                }
            }
            return aView;
        }

        private void overrideField(String sProp, ObjectHandle hValue) {
            FieldInfo field = getComposition().getFieldInfo(Nid.of(sProp));
            if (field == null || field.isTransient()) {
                throw new IllegalStateException("Cannot override field: " + sProp);
            }
            overrideField(field.getIndex(), hValue);
        }

        /**
         * If another view of this object already CONSTRUCTED the ref for the given inflated
         * field, return that constructed ref from the shared slot; null otherwise. A view whose
         * override layer still carries the pre-construction (struct-composition) clone must not
         * re-run annotation construction - the constructed ref is object-wide state, and the
         * stress harness proved re-construction dies on the (correctly) shared freeze state the
         * first construction's lazy set may have frozen (TestLiterals' @Lazy CPFileStore.root).
         */
        RefHandle sharedConstructedRef(FieldInfo field) {
            return m_aFields[field.getIndex()] instanceof RefHandle hShared
                    && !hShared.getComposition().isStruct()
                    ? hShared
                    : null;
        }

        /**
         * Publish a freshly constructed inflated-property ref as object-wide state: the shared
         * slot gets the constructed ref so every view can see construction happened, and this
         * view (whose override layer may still carry the stale pre-construction clone) adopts a
         * view-local $outer alias of it. Sibling views self-heal through
         * {@link #sharedConstructedRef} on their next access.
         */
        void publishConstructedRef(Frame frame, PropertyConstant idProp, RefHandle hRef) {
            FieldInfo field = getComposition().getFieldInfo(idProp);
            int       iPos  = field.getIndex();

            m_aFields[iPos] = hRef;
            if (hasFieldOverride(iPos)) {
                overrideField(iPos, viewLocalRefAlias(hRef));
            }
        }

        /**
         * @return a view-local alias of the (constructed) ref whose $outer is this view
         */
        RefHandle viewLocalRefAlias(RefHandle hRef) {
            RefHandle hAlias = (RefHandle) hRef.cloneAs(hRef.getComposition());
            ((GenericHandle) hAlias).overrideField(OUTER, this);
            return hAlias;
        }

        /**
         * Adopt a view-local alias of a ref another view constructed, replacing this view's
         * stale pre-construction override so later reads take the fast path.
         */
        RefHandle adoptConstructedRef(FieldInfo field, RefHandle hShared) {
            RefHandle hAlias = viewLocalRefAlias(hShared);
            overrideField(field.getIndex(), hAlias);
            return hAlias;
        }

        private void overrideField(int iPos, ObjectHandle hValue) {
            ObjectHandle[] aCurrent = m_aFieldOverrides;
            ObjectHandle[] aUpdated = aCurrent == null
                    ? new ObjectHandle[m_aFields.length]
                    : copyOf(aCurrent);
            aUpdated[iPos] = hValue;
            m_aFieldOverrides = aUpdated;
        }

        /**
         * The array of field values indexed according to the ClassComposition's field layout.
         */
        private final ObjectHandle[] m_aFields;

        /**
         * Optional access-view overrides layered over {@link #m_aFields}. This is intentionally
         * sparse and copy-on-write: the common path still reads the original field array directly,
         * while struct/revealed view clones can give inflated property refs a view-local $outer
         * without deep-copying all regular fields or mutating the source view's shared ref.
         */
        private volatile ObjectHandle[] m_aFieldOverrides;

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
            extends GenericHandle {
        public final String f_sRTError;

        /**
         * @param sRTError  if specified, indicates a *hidden* RT-error message to be logged to the
         *                  system console when an [obscured] exception text is being retrieved
         */
        public ExceptionHandle(TypeComposition clazz, String sRTError) {
            super(clazz);

            f_sRTError = sRTError;
        }

        public WrapperException getException() {
            return new WrapperException();
        }

        @Override
        public String toString() {
            // PURE: read "text" only when the field layout is already computed. getField(...) would
            // otherwise force the ClassComposition.f_fieldLayout Lazy cell - and Throwable.toString()
            // reaches here on ANY exception print, not just a debugger render.
            ObjectHandle hText = peekField("text");
            return super.toString() +
                (hText instanceof StringHandle hString
                    ? Handy.quotedString(hString.getStringValue())
                    : "<text deferred>");
        }

        public class WrapperException
                extends Exception {
            public WrapperException() {
                super();
            }

            public WrapperException(Throwable cause) {
                super(cause);
            }

            public ExceptionHandle getExceptionHandle() {
                return ExceptionHandle.this;
            }

            @Override
            public String toString() {
                return getExceptionHandle().toString();
            }
        }
    }

    /**
     * A handle for any object that fits in a long.
     */
    public static class JavaLong
            extends ObjectHandle
            implements IntegralValue {
        protected long m_lValue;

        public JavaLong(TypeComposition clazz, long lValue) {
            super(clazz);
            m_lValue = lValue;
        }

        @Override
        public boolean isSelfContained() {
            return true;
        }

        public long getValue() {
            return m_lValue;
        }

        @Override
        public boolean fitsLong(boolean fSigned) {
            return true;
        }

        @Override
        public long longValue() {
            return m_lValue;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(m_lValue);
        }

        @Override
        public int compareTo(ObjectHandle that) {
            return Long.compare(m_lValue, ((JavaLong) that).m_lValue);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof JavaLong that && m_lValue == that.m_lValue;
        }

        @Override
        public String toString() {
            return super.toString() + (getComposition().getTemplate() instanceof xChar
                    ? Handy.quotedChar((char) m_lValue)
                    : String.valueOf(m_lValue));
        }
    }

    /**
     * Native handle that holds a reference to a Constant from the ConstantPool.
     */
    public static class ConstantHandle
            extends ObjectHandle {
        public ConstantHandle(Container container, Constant constant) {
            super(NativeTemplates.get(container).object().getCanonicalClass());

            assert constant != null;
            f_constant = constant;
        }

        public Constant getConstant() {
            return f_constant;
        }

        @Override
        public String toString() {
            return f_constant.toString();
        }

        private final Constant f_constant;
    }

    /**
     * DeferredCallHandle represents a deferred action, such as a property access or a method call,
     * which would place the result of that action on the corresponding frame's stack.
     * <p>
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredCallHandle
            extends ObjectHandle {
        protected final Frame           f_frameNext;
        protected final ExceptionHandle f_hException;

        public DeferredCallHandle(Frame frameNext) {
            super(null);

            f_frameNext  = frameNext;
            f_hException = frameNext.m_hException;
        }

        public DeferredCallHandle(ExceptionHandle hException) {
            super(null);

            f_frameNext  = null;
            f_hException = hException;
        }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation) {
            if (f_hException == null) {
                Frame frameNext = f_frameNext;
                if (continuation != null) {
                    frameNext.addContinuation(continuation);
                }
                return frameCaller.call(frameNext);
            }

            return frameCaller.raiseException(f_hException);
        }

        public void addContinuation(Frame.Continuation continuation) {
            if (f_hException == null) {
                f_frameNext.addContinuation(continuation);
            }
        }

        @Override
        public boolean isPassThrough(Container container) {
            throw new IllegalStateException();
        }

        @Override
        public String toString() {
            return f_hException == null
                ? "Deferred call: " + f_frameNext
                : "Deferred exception: " + f_hException;
        }
    }

    /**
     * DeferredPropertyHandle represents a deferred property access, which would place the result
     * of that action on the corresponding frame's stack.
     * <p>
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredPropertyHandle
            extends DeferredCallHandle {
        private final PropertyConstant f_idProp;

        public DeferredPropertyHandle(PropertyConstant idProp) {
            super((ExceptionHandle) null);

            f_idProp = idProp;
        }

        @Override
        public void addContinuation(Frame.Continuation continuation) {
            throw new UnsupportedOperationException("a deferred property access cannot take a continuation: " + f_idProp);
        }

        public PropertyConstant getProperty() {
            return f_idProp;
        }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation) {
            ObjectHandle hThis = frameCaller.getThis();

            switch (hThis.getTemplate().getPropertyValue(frameCaller, hThis, f_idProp, Op.A_STACK)) {
            case Op.R_NEXT:
                return continuation.proceed(frameCaller);

            case Op.R_CALL:
                frameCaller.m_frameNext.addContinuation(continuation);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            case Op.R_REPEAT:
                return Op.R_REPEAT;

            default:
                throw new IllegalStateException();
            }
        }

        @Override
        public String toString() {
            return "Deferred property access: " + f_idProp.getName();
        }
    }

    /**
     * DeferredSingletonHandle represents a deferred singleton calculation, which would place the
     * result of that action on the corresponding frame's stack.
     * <p>
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredSingletonHandle
            extends DeferredCallHandle {
        private final SingletonConstant f_constSingleton;

        public DeferredSingletonHandle(SingletonConstant constSingleton) {
            super((ExceptionHandle) null);

            f_constSingleton = constSingleton;
        }

        @Override
        public void addContinuation(Frame.Continuation continuation) {
            throw new UnsupportedOperationException("a deferred singleton cannot take a continuation: " + f_constSingleton);
        }

        public SingletonConstant getConstant() {
            return f_constSingleton;
        }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation) {
            return Utils.initConstants(frameCaller, Collections.singletonList(f_constSingleton),
                frame -> {
                    frame.pushStack(f_constSingleton.getHandle());
                    return continuation.proceed(frame);
                });
        }

        @Override
        public String toString() {
            return "Deferred initialization for " + f_constSingleton;
        }
    }

    /**
     * DeferredArrayHandle represents a deferred array initialization, which would place the array
     * handle on the corresponding frame's stack.
     * <p>
     * Note: this handle cannot be allocated naturally and must be processed in a special way.
     */
    public static class DeferredArrayHandle
            extends DeferredCallHandle {
        private final TypeComposition f_clzArray;
        private final ObjectHandle[]  f_ahValue;

        public DeferredArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue) {
            super((ExceptionHandle) null);

            f_clzArray = clzArray;
            f_ahValue  = ahValue;
        }

        @Override
        public TypeConstant getType() {
            return augmentType(f_clzArray.getType());
        }

        @Override
        public ObjectHandle revealOrigin() {
            return this;
        }

        @Override
        public void addContinuation(Frame.Continuation continuation) {
            throw new UnsupportedOperationException("a deferred array cannot take a continuation: " + this);
        }

        @Override
        public int proceed(Frame frameCaller, Frame.Continuation continuation) {
            Frame.Continuation stepAssign = frame -> frame.pushStack(
                    xArray.createImmutableArray(f_clzArray, f_ahValue));

            switch (new Utils.GetArguments(f_ahValue, stepAssign).doNext(frameCaller)) {
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
        public String toString() {
            return "Deferred array initialization: " + getType();
        }
    }

    /**
     * A handle that is used for transient fields access.
     */
    public static class TransientId
            extends ObjectHandle {
        protected TransientId() {
            super(null);

            f_nHash = s_hashCode.getAndAdd(0x61c88647); // see ThreadLocal.java
        }

        @Override
        public int hashCode() {
            return f_nHash;
        }

        @Override
        public String toString() {
            return "Transient";
        }

        private final int f_nHash;

        private static final AtomicInteger s_hashCode = new AtomicInteger();
    }

    /**
     * A handle that is used during circular singleton initialization process.
     */
    public static class InitializingHandle
            extends ObjectHandle {
        private final SingletonConstant f_constSingleton;

        public InitializingHandle(SingletonConstant constSingleton) {
            super(null);

            f_constSingleton = constSingleton;
        }

        /**
         * @return the underlying initialized object or null
         */
        public ObjectHandle getInitialized() {
            ObjectHandle hConst = f_constSingleton.getHandle();
            return hConst == this ? null : hConst;
        }

        /**
         * @return the underlying initialized object
         * @throws IllegalStateException if the underlying object is not yet initialized
         */
        protected ObjectHandle assertInitialized() {
            ObjectHandle hConst = f_constSingleton.getHandle();
            if (hConst instanceof InitializingHandle) {
                throw new IllegalStateException("Circular initialization \"" +
                        f_constSingleton.getValue().getValueString() + '"');
            }
            return hConst;
        }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz) {
            return assertInitialized().cloneAs(clazz);
        }

        @Override
        public ObjectHandle revealOrigin() {
            return assertInitialized().revealOrigin();
        }

        @Override
        public List<String> validateFields() {
            return assertInitialized().validateFields();
        }

        @Override
        public boolean isSelfContained() {
            return assertInitialized().isSelfContained();
        }

        @Override
        public TypeComposition getComposition() {
            return assertInitialized().getComposition();
        }

        @Override
        public TypeConstant getType() {
            // we don't need to have a handle to answer the "type" question
            return f_constSingleton.getType();
        }

        @Override
        public boolean isPassThrough(Container container) {
            return assertInitialized().isPassThrough(container);
        }

        @Override
        public boolean isService() {
            return assertInitialized().isService();
        }

        @Override
        public ServiceHandle getService() {
            return assertInitialized().getService();
        }

        @Override
        public boolean isNativeEqual() {
            return assertInitialized().isNativeEqual();
        }

        @Override
        public ObjectHandle maskAs(Container owner, TypeConstant typeAs) {
            return assertInitialized().maskAs(owner, typeAs);
        }

        @Override
        public ObjectHandle revealAs(Frame frame, TypeConstant typeAs) {
            return assertInitialized().revealAs(frame, typeAs);
        }

        @Override
        public boolean isShared(Container container, Map<ObjectHandle, Boolean> mapVisited) {
            return assertInitialized().isShared(container, mapVisited);
        }

        @Override
        public int compareTo(ObjectHandle that) {
            return assertInitialized().compareTo(that);
        }

        @Override
        public int hashCode() {
            return assertInitialized().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return assertInitialized().equals(obj);
        }

        @Override
        public String toString() {
            ObjectHandle hConst = getInitialized();
            return hConst == null ? "<initializing>" : hConst.toString();
        }
    }

    /**
     * A handle that is used for blocking IO operations.
     */
    public static class NativeFutureHandle
            extends ObjectHandle {
        protected NativeFutureHandle(CompletableFuture<Object> cf) {
            super(null);

            f_future = cf;
        }

        @Override
        public String toString() {
            return "Native: " + f_future;
        }

        public final CompletableFuture<Object> f_future;
    }
    /**
     * A handle that is used as an indicator for a default method argument value.
     */
    public static final ObjectHandle DEFAULT = new ObjectHandle(null) {
        @Override
        public TypeConstant getType() {
            return null;
        }

        @Override
        public String toString() {
            return "<default>";
        }
    };
}
