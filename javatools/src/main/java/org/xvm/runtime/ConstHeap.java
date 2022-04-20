package org.xvm.runtime;


import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.RegisterConstant;
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
        if (constValue instanceof RegisterConstant constReg)
            {
            return constReg.getHandle(frame);
            }

        // NOTE: we cannot use computeIfAbsent, since createConstHandle can be recursive,
        // and ConcurrentHashMap is not recursion friendly
        ObjectHandle hValue = getConstHandle(constValue);
        if (hValue != null)
            {
            return hValue;
            }

        if (constValue instanceof SingletonConstant constSingleton)
            {
            hValue = constSingleton.getHandle();
            return hValue == null
                ? new DeferredSingletonHandle(constSingleton)
                : saveConstHandle(constValue, hValue);
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
    protected ObjectHandle getConstHandle(Constant constValue)
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
    protected ObjectHandle saveConstHandle(Constant constValue, ObjectHandle hValue)
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

        ObjectHandle hValue0 = f_mapConstants.putIfAbsent(constValue, hValue);
        return hValue0 == null ? hValue : hValue0;
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