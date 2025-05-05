package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.OpMove;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;


/**
 * MOV_TYPE rvalue-src, lvalue-dest; place the type of the r-value (sans explicit immutability) into the l-value
 *
 * Note: at the moment, this op is only used to facilitate the virtual construction
 */
public class MoveType
        extends OpMove {
    /**
     * Construct a MOV_TYPE op for the passed arguments.
     *
     * @param argSrc   the source Argument
     * @param argDest  the destination Argument
     */
    public MoveType(Argument argSrc, Argument argDest) {
        super(argSrc, argDest);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveType(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_MOV_TYPE;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nFromValue);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        complete(frameCaller, frameCaller.popStack()))
                    : complete(frame, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, ObjectHandle hValue) {
        ConstantPool pool = frame.poolContext();
        int          nTo  = m_nToValue;
        TypeConstant type = hValue.getComposition().getType(); // don't augment the value type

        if (frame.isNextRegister(nTo)) {
            frame.introduceResolvedVar(nTo,
                pool.ensureParameterizedTypeConstant(pool.typeType(), type));
        }
        return frame.assignValue(nTo, type.ensureTypeHandle(frame.f_context.f_container));
    }
}