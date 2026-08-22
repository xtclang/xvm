package org.xvm.runtime;


import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

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
 *
 * <p>A heap is stored on a {@link Container}, but it deliberately does not retain that owner.
 * {@link Container}'s base constructor creates the heap before subclass construction is complete;
 * keeping the owner explicit avoids a constructor-time {@code this} escape while preserving the
 * same per-container cache map.</p>
 */
public class ConstHeap {
    /**
     * Create an ownerless constant heap. Callers pass the owner container to each operation.
     */
    public ConstHeap() {}

    /**
     * Return a handle for the specified constant (could be DeferredCallHandle).
     *
     * @param constValue "literal" (Int/String/etc.) constant known by the frame's context pool
     *
     * @return an ObjectHandle (could be DeferredCallHandle representing a call or an exception)
     */
    protected ObjectHandle ensureConstHandle(Container container, Frame frame, Constant constValue) {
        Container owner = requireNonNull(container, "container");

        if (constValue instanceof FrameDependentConstant constFrame) {
            return constFrame.getHandle(frame);
        }

        // NOTE: we cannot use computeIfAbsent, since createConstHandle can be recursive,
        // and ConcurrentHashMap is not recursion friendly
        ObjectHandle hValue = getConstHandle(owner, constValue);
        if (hValue != null) {
            return hValue;
        }

        if (constValue instanceof SingletonConstant constSingle) {
            hValue = constSingle.getHandle();
            if (hValue != null) {
                if (hValue instanceof xLazy.LazyHandle hLazy && !hLazy.isAssigned()) {
                    // compute the lazy value now
                    switch (hLazy.getVarSupport().getReferent(frame, hLazy, Op.A_STACK)) {
                    case Op.R_NEXT:
                        hValue = frame.popStack();
                        break;

                    case Op.R_CALL: {
                        Frame frameNext = frame.m_frameNext;
                        frameNext.addContinuation(frameCaller -> {
                            saveConstHandle(owner, constValue, frameCaller.peekStack());
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
                return saveConstHandle(owner, constValue, hValue);
            }

            // make sure we don't leak a singleton handle into the parent's container pool
            ConstantPool pooThis = frame.poolContext();
            if (constSingle.getConstantPool() != pooThis) {
                Container containerThis = frame.container();
                Container containerOrig = containerThis.getOriginContainer(constSingle);

                constSingle = containerOrig.getConstantPool().register(constSingle);
                hValue      = constSingle.getHandle();
                if (hValue != null) {
                    return saveConstHandle(owner, constSingle, hValue);
                }
            }

            return new DeferredSingletonHandle(constSingle);
        }

        // support for the "local property" mode
        if (constValue instanceof PropertyConstant idProp) {
            assert !idProp.isConstant();

            ConstantPool pooThis = frame.poolContext();
            if (idProp.getConstantPool() != pooThis) {
                idProp = pooThis.register(idProp);
            }

            return saveConstHandle(owner, constValue, new DeferredPropertyHandle(idProp));
        }

        switch (owner.getTemplate(constValue).createConstHandle(frame, constValue)) {
        case Op.R_NEXT: {
            hValue = frame.popStack();
            return constValue.isValueCacheable()
                ? saveConstHandle(owner, constValue, hValue)
                : hValue;
        }

        case Op.R_CALL:
            Frame frameNext = frame.m_frameNext;
            if (constValue.isValueCacheable()) {
                frameNext.addContinuation(frameCaller -> {
                    saveConstHandle(owner, constValue, frameCaller.peekStack());
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
     * @return saved handle or null
     */
    public ObjectHandle getConstHandle(Container container, Constant constValue) {
        Container owner  = requireNonNull(container, "container");
        ObjectHandle hValue = f_mapConstants.get(constValue);
        if (hValue == null) {
            Container containerParent = owner.f_parent;
            if (containerParent != null) {
                hValue = containerParent.getConstHeap().getConstHandle(containerParent, constValue);

                // there is a chance that both our child and our parent do "know" that value's type,
                // but it's not a part of our type system
                if (hValue != null && hValue.isShared(owner, null)) {
                    saveConstHandle(owner, constValue, hValue);
                }
            }
        }
        return hValue;
    }

    /**
     * @return saved handle of the requested type, or null
     */
    public <H extends ObjectHandle> H getConstHandle(
            Container container, Constant constValue, Class<H> clzHandle) {
        ObjectHandle hValue = getConstHandle(container, constValue);
        return hValue == null ? null : clzHandle.cast(hValue);
    }

    /**
     * Save the handle for a constant.
     *
     * @param constValue  the constant
     * @param hValue      the handle
     *
     * @return the actual handle
     */
    public ObjectHandle saveConstHandle(Container container, Constant constValue, ObjectHandle hValue) {
        Container owner = requireNonNull(container, "container");

        if (hValue instanceof InitializingHandle hInit) {
            ObjectHandle hConst = hInit.getInitialized();
            if (hConst == null) {
                return hValue;
            }
            hValue = hConst;
        }
        ConstantPool pool = owner.getConstantPool();
        if (constValue.getConstantPool() != pool) {
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
    public ObjectHandle relocateConst(Container container, ObjectHandle hConst, Constant constant) {
        Container owner  = requireNonNull(container, "container");
        Container parent = owner.f_parent;
        if (parent != null && hConst.isShared(parent, null)) {
            ObjectHandle hNew = parent.getConstHeap().relocateConst(parent, hConst, constant);

            // we could also re-insert it right away (after re-registering the constant)
            f_mapConstants.remove(constant);
            return hNew;
        }

        ObjectHandle hPrev = getConstHandle(owner, constant);
        if (hPrev != null) {
            // we have it; no need to do anything
            return hPrev;
        }

        ConstantPool pool = owner.getConstantPool();
        if (constant.getConstantPool() != pool) {
            constant = pool.register(constant);
        }

        return f_mapConstants.computeIfAbsent(constant, c -> {
            ObjectHandle hNew = hConst.getComposition().getContainer() == owner
                ? hConst
                : hConst.cloneAs(hConst.getTemplate().ensureClass(owner, hConst.getType()));

            if (c instanceof SingletonConstant constSingleton) {
                constSingleton.setHandle(hNew);
            }
            return hNew;
        });
    }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The cached constants.
     */
    private final Map<Constant, ObjectHandle> f_mapConstants = new ConcurrentHashMap<>();
}
