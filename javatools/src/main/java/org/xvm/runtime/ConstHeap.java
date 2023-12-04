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


/**
 * The heap of Constant handles.
 */
public class ConstHeap
    {
    /**
     * Create a constant heap for the specified Container.
     */
    public ConstHeap(Container container)
        {
        f_container = container;
        }

    /**
     * Return a handle for the specified constant (could be DeferredCallHandle).
     *
     * @param constValue "literal" (Int/String/etc.) constant known by the frame's context pool
     *
     * @return an ObjectHandle (could be DeferredCallHandle representing a call or an exception)
     */
    protected ObjectHandle ensureConstHandle(Frame frame, Constant constValue)
        {
        if (constValue instanceof FrameDependentConstant constFrame)
            {
            return constFrame.getHandle(frame);
            }

        // NOTE: we cannot use computeIfAbsent, since createConstHandle can be recursive,
        // and ConcurrentHashMap is not recursion friendly
        ObjectHandle hValue = getConstHandle(constValue);
        if (hValue != null)
            {
            return hValue;
            }

        if (constValue instanceof SingletonConstant constSingle)
            {
            hValue = constSingle.getHandle();
            if (hValue != null)
                {
                return saveConstHandle(constValue, hValue);
                }

            // make sure we don't leak a singleton handle into the parent's container pool
            ConstantPool pooThis = frame.poolContext();
            if (constSingle.getConstantPool() != pooThis)
                {
                Container containerThis = frame.f_context.f_container;
                Container containerOrig = containerThis.getOriginContainer(constSingle);

                constSingle = (SingletonConstant) containerOrig.getConstantPool().register(constSingle);
                hValue      = constSingle.getHandle();
                if (hValue != null)
                    {
                    return saveConstHandle(constSingle, hValue);
                    }
                }

            return new DeferredSingletonHandle(constSingle);
            }

        // support for the "local property" mode
        if (constValue instanceof PropertyConstant idProp)
            {
            assert !idProp.isConstant();

            return saveConstHandle(constValue, new DeferredPropertyHandle(idProp));
            }

        switch (f_container.getTemplate(constValue).createConstHandle(frame, constValue))
            {
            case Op.R_NEXT:
                {
                hValue = frame.popStack();
                return constValue.isValueCacheable()
                    ? saveConstHandle(constValue, hValue)
                    : hValue;
                }

            case Op.R_CALL:
                Frame frameNext = frame.m_frameNext;
                if (constValue.isValueCacheable())
                    {
                    frameNext.addContinuation(frameCaller ->
                        {
                        saveConstHandle(constValue, frameCaller.peekStack());
                        return Op.R_NEXT;
                        });
                    }
                return new DeferredCallHandle(frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return saved handle or null
     */
    public ObjectHandle getConstHandle(Constant constValue)
        {
        ObjectHandle hValue = f_mapConstants.get(constValue);
        if (hValue == null)
            {
            Container containerParent = f_container.f_parent;
            if (containerParent != null)
                {
                hValue = containerParent.f_heap.getConstHandle(constValue);
                if (hValue != null)
                    {
                    saveConstHandle(constValue, hValue);
                    }
                }
            }
        return hValue;
        }

    /**
     * Save the handle for a constant.
     *
     * @param constValue  the constant
     * @param hValue      the handle
     *
     * @return the actual handle
     */
    public ObjectHandle saveConstHandle(Constant constValue, ObjectHandle hValue)
        {
        if (hValue instanceof InitializingHandle hInit)
            {
            ObjectHandle hConst = hInit.getInitialized();
            if (hConst == null)
                {
                return hValue;
                }
            hValue = hConst;
            }
        ConstantPool pool = f_container.getConstantPool();
        if (constValue.getConstantPool() != pool)
            {
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
     * @param hConst  the constant handle to relocate
     * @param const   the constant for the handle
     *
     * @return the relocated handle or null if cannot be relocated
     */
    public ObjectHandle relocateConst(ObjectHandle hConst, Constant constant)
        {
        Container parent = f_container.f_parent;
        if (parent != null && hConst.isShared(parent.getConstantPool(), null))
            {
            ObjectHandle hNew = parent.f_heap.relocateConst(hConst, constant);

            // we could also re-insert it right away (after re-registering the constant)
            f_mapConstants.remove(constant);
            return hNew;
            }

        ObjectHandle hPrev = getConstHandle(constant);
        if (hPrev != null)
            {
            // we have it; no need to do anything
            return hPrev;
            }

        ConstantPool pool = f_container.getConstantPool();
        if (constant.getConstantPool() != pool)
            {
            constant = pool.register(constant);
            }

        return f_mapConstants.computeIfAbsent(constant, c ->
            {
            ObjectHandle hNew = hConst.cloneAs(
                    hConst.getTemplate().ensureClass(f_container, hConst.getType()));

            if (c instanceof SingletonConstant constSingleton)
                {
                constSingleton.setHandle(hNew);
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
    }