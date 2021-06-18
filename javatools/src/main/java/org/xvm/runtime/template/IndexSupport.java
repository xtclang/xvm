package org.xvm.runtime.template;


import java.util.function.Consumer;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.IndexedRefHandle;
import org.xvm.runtime.template.reflect.xVar;


/**
 * Support for index-based (array or tuple) op-codes.
 */
public interface IndexSupport
    {
    /**
     * Extract an array or tuple element at a given index.
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     * @param iReturn  the register id to place the extracted element into
     *
     * @return {@link Op#R_NEXT} or {@link Op#R_EXCEPTION}
     */
    int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn);

    /**
     * Place the specified element into an array or tuple at a given index.
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     *
     * @return {@link Op#R_NEXT} or {@link Op#R_EXCEPTION}
     */
    int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue);

    /**
     * @return the [declared] element type at the specified index
     */
     TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException;

    /**
     * @return the element count
     */
    long size(ObjectHandle hTarget);

    /**
     * Place a Var/Ref to the element value at the specified index into the specified register
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     * @param iReturn  the register id to place the extracted element into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    default int makeRef(Frame frame, ObjectHandle hTarget, long lIndex, boolean fReadOnly, int iReturn)
        {
        try
            {
            TypeConstant typeEl = getElementType(frame, hTarget, lIndex);

            TypeComposition clzRef = fReadOnly
                ? xRef.INSTANCE.ensureParameterizedClass(frame.poolContext(), typeEl)
                : xVar.INSTANCE.ensureParameterizedClass(frame.poolContext(), typeEl);

            IndexedRefHandle hRef = new IndexedRefHandle(clzRef, hTarget, lIndex);

            return frame.assignValue(iReturn, hRef);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    /**
     * Increment the element value and place the result into the specified register.
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     * @param iReturn  the register id to place the extracted element into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    default int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ObjectHandle hValue;
        switch (extractArrayValue(frame, hTarget, lIndex, Op.A_STACK))
            {
            case Op.R_NEXT:
                hValue = frame.popStack();
                break;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                // for now, virtual array ops are not supported
                throw new IllegalStateException();
            }

        switch (hValue.getOpSupport().invokeNext(frame, hValue, Op.A_STACK))
            {
            case Op.R_NEXT:
                {
                ObjectHandle hValueNew = frame.popStack();
                return assignArrayValue(frame, hTarget, lIndex, hValueNew) == Op.R_EXCEPTION ?
                    Op.R_EXCEPTION : frame.assignValue(iReturn, hValueNew);
                }

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    ObjectHandle hValueNew = frameCaller.popStack();
                    return assignArrayValue(frameCaller, hTarget, lIndex, hValueNew) == Op.R_EXCEPTION ?
                        Op.R_EXCEPTION : frameCaller.assignValue(iReturn, hValueNew);
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Place the element value into the specified register and increment the value.
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     * @param iReturn  the register id to place the extracted element into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    default int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ObjectHandle hValue;
        switch (extractArrayValue(frame, hTarget, lIndex, Op.A_STACK))
            {
            case Op.R_NEXT:
                hValue = frame.popStack();
                break;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                // for now, virtual array ops are not supported
                throw new IllegalStateException();
            }

        switch (hValue.getOpSupport().invokeNext(frame, hValue, Op.A_STACK))
            {
            case Op.R_NEXT:
                return assignArrayValue(frame, hTarget, lIndex, frame.popStack()) == Op.R_EXCEPTION ?
                    Op.R_EXCEPTION : frame.assignValue(iReturn, hValue);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    assignArrayValue(frameCaller, hTarget, lIndex, frameCaller.popStack()) == Op.R_EXCEPTION ?
                        Op.R_EXCEPTION : frameCaller.assignValue(iReturn, hValue));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Decrement the element value and place the result into the specified register.
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     * @param iReturn  the register id to place the extracted element into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    default int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ObjectHandle hValue;
        switch (extractArrayValue(frame, hTarget, lIndex, Op.A_STACK))
            {
            case Op.R_NEXT:
                hValue = frame.popStack();
                break;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                // for now, virtual array ops are not supported
                throw new IllegalStateException();
            }

        switch (hValue.getOpSupport().invokePrev(frame, hValue, Op.A_STACK))
            {
            case Op.R_NEXT:
                {
                ObjectHandle hValueNew = frame.popStack();
                return assignArrayValue(frame, hTarget, lIndex, hValueNew) == Op.R_EXCEPTION ?
                    Op.R_EXCEPTION : frame.assignValue(iReturn, hValueNew);
                }

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    ObjectHandle hValueNew = frameCaller.popStack();
                    return assignArrayValue(frameCaller, hTarget, lIndex, hValueNew) == Op.R_EXCEPTION ?
                        Op.R_EXCEPTION : frameCaller.assignValue(iReturn, hValueNew);
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Place the element value into the specified register and decrement the value.
     *
     * @param frame    the current frame
     * @param hTarget  the array or tuple handle
     * @param lIndex   the element index
     * @param iReturn  the register id to place the extracted element into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    default int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ObjectHandle hValue;
        switch (extractArrayValue(frame, hTarget, lIndex, Op.A_STACK))
            {
            case Op.R_NEXT:
                hValue = frame.popStack();
                break;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                // for now, virtual array ops are not supported
                throw new IllegalStateException();
            }

        switch (hValue.getOpSupport().invokePrev(frame, hValue, Op.A_STACK))
            {
            case Op.R_NEXT:
                return assignArrayValue(frame, hTarget, lIndex, frame.popStack()) == Op.R_EXCEPTION ?
                    Op.R_EXCEPTION : frame.assignValue(iReturn, hValue);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    assignArrayValue(frameCaller, hTarget, lIndex, frame.popStack()) == Op.R_EXCEPTION ?
                        Op.R_EXCEPTION : frameCaller.assignValue(iReturn, hValue));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    // trivial helpers
    default ObjectHandle[] toArray(Frame frame, ObjectHandle hTarget)
            throws ExceptionHandle.WrapperException
        {
        int            cValues = (int) size(hTarget);
        ObjectHandle[] ahValue = new ObjectHandle[cValues];

        for (int i = 0; i < cValues; i++)
            {
            switch (extractArrayValue(frame, hTarget, i, Op.A_STACK))
                {
                case Op.R_NEXT:
                    ahValue[i] = frame.popStack();
                    break;

                case Op.R_EXCEPTION:
                    throw frame.m_hException.getException();

                default:
                    // for now, virtual array ops are not supported
                    throw new IllegalStateException();
                }
            }
        return ahValue;
        }

    default void forEach(Frame frame, ObjectHandle hTarget, Consumer<ObjectHandle> consumer)
            throws ExceptionHandle.WrapperException
        {
        int cValues = (int) size(hTarget);

        for (int i = 0; i < cValues; i++)
            {
            switch (extractArrayValue(frame, hTarget, i, Op.A_STACK))
                {
                case Op.R_NEXT:
                    consumer.accept(frame.popStack());
                    break;

                case Op.R_EXCEPTION:
                    throw frame.m_hException.getException();

                default:
                    // for now, virtual array ops are not supported
                    throw new IllegalStateException();
                }
            }
        }
    }
