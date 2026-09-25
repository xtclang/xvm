package org.xvm.runtime;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FrameDependentConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.DeferredPropertyHandle;
import org.xvm.runtime.ObjectHandle.DeferredSingletonHandle;
import org.xvm.runtime.ObjectHandle.InitializingHandle;
import org.xvm.runtime.template.annotations.xLazy;

/**
 * The heap of Constant handles.
 */
public class ConstHeap {
    /**
     * Create a constant heap for the specified Container.
     */
    public ConstHeap(Container container) {
        f_container = container;
    }

    /**
     * Return a handle for the specified constant (could be DeferredCallHandle).
     *
     * @param constant  "literal" (Int/String/etc.) constant known by the frame's context pool
     *
     * @return an ObjectHandle (could be DeferredCallHandle representing a call or an exception)
     */
    protected ObjectHandle ensureConstHandle(Frame frame, Constant constant) {
        // A local alias of a core/shared singleton still uses its origin container's value.
        // Resolve ownership before either the cached-handle or owner-local state lookup.
        Constant constValue = constant instanceof SingletonConstant singleton
                ? f_container.ensureSingletonConstant(singleton)
                : constant;
        if (constValue instanceof FrameDependentConstant constFrame) {
            return constFrame.getHandle(frame);
        }

        // NOTE: we cannot use computeIfAbsent, since createConstHandle can be recursive,
        // and ConcurrentHashMap is not recursion friendly
        ObjectHandle hValue = getConstHandle(constValue);
        if (hValue != null) {
            return hValue;
        }

        if (constValue instanceof SingletonConstant constSingle) {
            hValue = f_container.ensureSingletonState(constSingle).getHandle();
            if (hValue != null) {
                if (hValue instanceof xLazy.LazyHandle hLazy) {
                    // A ref may already have assigned it; constant access still needs the referent.
                    switch (hLazy.getVarSupport().getReferent(frame, hLazy, Op.A_STACK)) {
                    case Op.R_NEXT:
                        hValue = frame.popStack();
                        break;

                    case Op.R_CALL: {
                        Frame frameNext = frame.m_frameNext;
                        frameNext.addContinuation(frameCaller -> {
                            saveConstHandle(constValue, frameCaller.peekStack());
                            return Op.R_NEXT;
                        });
                        return new DeferredCallHandle(frameNext);
                    }

                    case Op.R_EXCEPTION:
                        return new DeferredCallHandle(frame.clearException());

                    default:
                        throw new IllegalStateException();
                    }
                }
                return saveConstHandle(constValue, hValue);
            }

            return new DeferredSingletonHandle(constSingle);
        }

        // support for the "local property" mode
        if (constValue instanceof PropertyConstant idProp) {
            assert !idProp.isConstant();

            idProp = frame.runtimeConstant(idProp);

            return saveConstHandle(constValue, new DeferredPropertyHandle(idProp));
        }

        switch (f_container.getTemplate(constValue).createConstHandle(frame, constValue)) {
        case Op.R_NEXT: {
            hValue = frame.popStack();
            return constValue.isValueCacheable()
                ? saveConstHandle(constValue, hValue)
                : hValue;
        }

        case Op.R_CALL:
            Frame frameNext = frame.m_frameNext;
            if (constValue.isValueCacheable()) {
                frameNext.addContinuation(frameCaller -> {
                    saveConstHandle(constValue, frameCaller.peekStack());
                    return Op.R_NEXT;
                });
            }
            return new DeferredCallHandle(frameNext);

        case Op.R_EXCEPTION:
            return new DeferredCallHandle(frame.clearException());

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Find a cached value in this heap or a parent whose value is shared with this container.
     * Equal constants alone do not establish compatible runtime types or singleton ownership.
     *
     * @return a local or shareable parent handle, or null
     */
    public ObjectHandle getConstHandle(Constant constValue) {
        ObjectHandle hValue = f_mapConstants.get(constValue);
        if (hValue == null) {
            if (constValue instanceof SingletonConstant singleton &&
                    f_container.getOriginContainer(singleton) == f_container) {
                // Type compatibility does not imply shared singleton state. An unshared child
                // can know the same module definitions as its parent and still own a fresh value.
                return null;
            }
            Container containerParent = f_container.f_parent;
            if (containerParent != null) {
                hValue = containerParent.f_heap.getConstHandle(constValue);

                // there is a chance that both our child and our parent do "know" that value's type,
                // but it's not a part of our type system
                if (hValue != null) {
                    return hValue.isShared(f_container, null)
                            ? saveConstHandle(constValue, hValue)
                            : null;
                }
            }
        }
        return hValue;
    }

    /**
     * Obtain a local singleton entry after {@link Container#ensureSingletonState} has selected
     * this heap as the value owner and canonicalized the definition in its pool. Creating the
     * entry performs no initialization, so atomic insertion cannot recurse into user code.
     *
     * @param definition  the owner-canonical singleton definition
     *
     * @return this heap's unique state entry
     */
    SingletonState ensureSingletonState(SingletonConstant definition) {
        assert definition.getConstantPool() == f_container.getConstantPool()
                || definition.getConstantPool() == f_container.getTypeContext().getDescriptorPool();
        return singletonStates.computeIfAbsent(definition, SingletonState::new);
    }

    /**
     * Save the handle for a constant.
     *
     * @param constValue  the constant
     * @param hValue      the handle
     *
     * @return the actual handle
     */
    public ObjectHandle saveConstHandle(Constant constValue, ObjectHandle hValue) {
        if (hValue instanceof InitializingHandle hInit) {
            ObjectHandle hConst = hInit.getInitialized();
            if (hConst == null) {
                return hValue;
            }
            hValue = hConst;
        }
        ConstantPool pool = f_container.getConstantPool();
        if (constValue.getConstantPool() != pool && constValue.getConstantPool().hasSerializedIndices()) {
            constValue = pool.register(constValue);
        }
        ObjectHandle hValue0 = f_mapConstants.putIfAbsent(constValue, hValue);
        return hValue0 == null ? hValue : hValue0;
    }

    /**
     * Most commonly, we try to keep cached constants at the highest applicable container, avoiding
     * polluting the parent container with potentially unused constants. However, when the constant
     * needs to be cached by someone non-related to this container, we need to relocate such a
     * constant to a lower container to avoid a leak (preventing this container to be GC'd).
     *
     * @param hConst   the constant handle to relocate
     * @param constant the constant for the handle
     *
     * @return the relocated handle or null if cannot be relocated
     */
    public ObjectHandle relocateConst(ObjectHandle hConst, Constant constant) {
        Container parent = f_container.f_parent;
        if (parent != null && hConst.isShared(parent, null)) {
            ObjectHandle hNew = parent.f_heap.relocateConst(hConst, constant);

            // we could also re-insert it right away (after re-registering the constant)
            f_mapConstants.remove(constant);
            return hNew;
        }

        ObjectHandle hPrev = getConstHandle(constant);
        if (hPrev != null) {
            // we have it; no need to do anything
            return hPrev;
        }

        ConstantPool pool = f_container.getConstantPool();
        if (constant.getConstantPool() != pool) {
            constant = pool.register(constant);
        }

        return f_mapConstants.computeIfAbsent(constant, c -> {
            ObjectHandle hNew = hConst.getComposition().getContainer() == f_container
                ? hConst
                : hConst.cloneAs(
                    hConst.getTemplate().ensureClass(f_container, hConst.getType()));

            if (c instanceof SingletonConstant constSingleton) {
                f_container.ensureSingletonState(constSingleton).setHandle(hNew);
            }
            return hNew;
        });
    }

    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The container this heap belongs to.
     */
    protected final Container f_container;

    /**
     * The cached constants.
     */
    private final Map<Constant, ObjectHandle> f_mapConstants = new ConcurrentHashMap<>();

    /**
     * Live singleton state for this owner, independent of the definition object's lifetime.
     */
    private final Map<SingletonConstant, SingletonState> singletonStates = new ConcurrentHashMap<>();
}
