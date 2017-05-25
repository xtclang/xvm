package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xRef.IndexedRefHandle;

/**
 * Support for UniformIndex (array or tuple) op-codes.
 *
 * @author gg 2017.05.15
 */
public interface IndexSupport
    {
    // @op "get" support - place the element value into the specified frame register
    ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException;

    // @op "set" support - assign the element value
    ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue);

    // obtain the [declared] element type
    Type getElementType(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException;

    // @op "elementAt" support - place a Ref to the element value into the specified register
    default int makeRef(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            Type typeReferent = getElementType(hTarget, lIndex);

            TypeComposition clzRef = xRef.INSTANCE.resolve(new Type[]{typeReferent});

            IndexedRefHandle hRef = new IndexedRefHandle(clzRef, hTarget, lIndex);

            return frame.assignValue(iReturn, hRef);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return Op.R_EXCEPTION;
            }
        }

    // @op "preInc" support - increment the element value and place the result into the specified register
    // return one of the Op.R_ values or zero
    default int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            ObjectHandle hValue = extractArrayValue(hTarget, lIndex);

            return hValue.f_clazz.f_template.
                    invokePreInc(frame, hValue, null, iReturn);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return Op.R_EXCEPTION;
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
