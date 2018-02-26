package org.xvm.runtime.template;


import java.util.function.Consumer;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xRef.IndexedRefHandle;


/**
 * Support for index-based (array or tuple) op-codes.
 */
public interface IndexSupport
    {
    // @Op "get" support - place the element value into the specified frame register
    ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException;

    // @Op "set" support - assign the element value
    ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue);

    // obtain the [declared] element type
    TypeConstant getElementType(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException;

    // get the element count
    long size(ObjectHandle hTarget);

    // @Op "elementAt" support - place a Var/Ref to the element value into the specified register
    default int makeRef(Frame frame, ObjectHandle hTarget, long lIndex, boolean fReadOnly, int iReturn)
        {
        try
            {
            TypeConstant typeEl = getElementType(hTarget, lIndex);

            TypeComposition clzRef = fReadOnly
                ? xRef.INSTANCE.ensureParameterizedClass(typeEl)
                : xVar.INSTANCE.ensureParameterizedClass(typeEl);

            IndexedRefHandle hRef = new IndexedRefHandle(clzRef, hTarget, lIndex);

            return frame.assignValue(iReturn, hRef);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    // @Op "preInc" support - increment the element value and place the result into the specified register
    // return one of the Op.R_ values or zero
    default int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            ObjectHandle hValue = extractArrayValue(hTarget, lIndex);

            switch (hValue.getOpSupport().invokeNext(frame, hValue, Frame.RET_LOCAL))
                {
                case Op.R_NEXT:
                    {
                    ObjectHandle hValueNew = frame.getFrameLocal();
                    assignArrayValue(hTarget, lIndex, hValueNew);
                    return frame.assignValue(iReturn, hValueNew);
                    }

                case Op.R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        ObjectHandle hValueNew = frameCaller.getFrameLocal();
                        assignArrayValue(hTarget, lIndex, hValueNew);
                        return frameCaller.assignValue(iReturn, hValueNew);
                        });
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    // @Op "postInc" support - place the result into the specified register and increment the element value
    // return one of the Op.R_ values or zero
    default int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            ObjectHandle hValue = extractArrayValue(hTarget, lIndex);

            switch (hValue.getOpSupport().invokeNext(frame, hValue, Frame.RET_LOCAL))
                {
                case Op.R_NEXT:
                    assignArrayValue(hTarget, lIndex, frame.getFrameLocal());
                    return frame.assignValue(iReturn, hValue);

                case Op.R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        assignArrayValue(hTarget, lIndex, frame.getFrameLocal());
                        return frameCaller.assignValue(iReturn, hValue);
                        });
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    // @Op "preDec" support - decrement the element value and place the result into the specified register
    // return one of the Op.R_ values or zero
    default int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            ObjectHandle hValue = extractArrayValue(hTarget, lIndex);

            switch (hValue.getOpSupport().invokePrev(frame, hValue, Frame.RET_LOCAL))
                {
                case Op.R_NEXT:
                    {
                    ObjectHandle hValueNew = frame.getFrameLocal();
                    assignArrayValue(hTarget, lIndex, hValueNew);
                    return frame.assignValue(iReturn, hValueNew);
                    }

                case Op.R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        ObjectHandle hValueNew = frameCaller.getFrameLocal();
                        assignArrayValue(hTarget, lIndex, hValueNew);
                        return frameCaller.assignValue(iReturn, hValueNew);
                        });
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    // @Op "postDec" support - place the result into the specified register and decrement the element value
    // return one of the Op.R_ values or zero
    default int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            ObjectHandle hValue = extractArrayValue(hTarget, lIndex);

            switch (hValue.getOpSupport().invokePrev(frame, hValue, Frame.RET_LOCAL))
                {
                case Op.R_NEXT:
                    assignArrayValue(hTarget, lIndex, frame.getFrameLocal());
                    return frame.assignValue(iReturn, hValue);

                case Op.R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        assignArrayValue(hTarget, lIndex, frame.getFrameLocal());
                        return frameCaller.assignValue(iReturn, hValue);
                        });
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    // trivial helpers
    default ObjectHandle[] toArray(ObjectHandle hTarget)
            throws ExceptionHandle.WrapperException
        {
        int cValues = (int) size(hTarget);
        ObjectHandle[] ahValue = new ObjectHandle[cValues];

        for (int i = 0; i < cValues; i++)
            {
            ahValue[i] = extractArrayValue(hTarget, i);
            }
        return ahValue;
        }

    default void forEach(ObjectHandle hTarget, Consumer<ObjectHandle> consumer)
            throws ExceptionHandle.WrapperException
        {
        int cValues = (int) size(hTarget);

        for (int i = 0; i < cValues; i++)
            {
            consumer.accept(extractArrayValue(hTarget, i));
            }
        }

    // ----- helpers -----

    static ExceptionHandle outOfRange(long lIndex, long cSize)
        {
        return xException.makeHandle(lIndex < 0 ?
                "Negative index: " + lIndex :
                "Array index " + lIndex + " out of range 0.." + cSize);
        }
    }
