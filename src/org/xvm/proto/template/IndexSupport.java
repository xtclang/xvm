package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

/**
 * Support for UniformIndex (array) op-codes.
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

    // @op "preInc" support - increment the element value and place the result into the specified register
    ExceptionHandle invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn);

    // ----- helpers -----
    static ExceptionHandle outOfRange(long lIndex, long cSize)
        {
        return xException.makeHandle("Array index " + lIndex + " out of range 0.." + cSize);
        }
    }
