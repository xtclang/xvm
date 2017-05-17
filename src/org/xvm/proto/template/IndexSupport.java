package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

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
    default ExceptionHandle makeRef(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
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
            return e.getExceptionHandle();
            }
        }

    // @op "preInc" support - increment the element value and place the result into the specified register
    default ExceptionHandle invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        try
            {
            ObjectHandle hValue = extractArrayValue(hTarget, lIndex);

            ExceptionHandle hException = hValue.f_clazz.f_template.
                    invokePreInc(frame, hValue, null, Frame.R_FRAME);
            if (hException != null)
                {
                return hException;
                }

            return frame.assignValue(iReturn, frame.getFrameLocal());
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return e.getExceptionHandle();
            }
        }

    // ----- helpers -----

    static ExceptionHandle outOfRange(long lIndex, long cSize)
        {
        return xException.makeHandle("Array index " + lIndex + " out of range 0.." + cSize);
        }
    }
