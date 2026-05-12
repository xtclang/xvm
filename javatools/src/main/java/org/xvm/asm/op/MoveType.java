package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.Builder.CD_nType;


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
        int          nTo  = m_nToValue;
        TypeConstant type = hValue.getComposition().getType(); // don't augment the value type

        if (frame.isNextRegister(nTo)) {
            frame.introduceResolvedVar(nTo, type.getType());
        }
        return frame.assignValue(nTo, type.ensureTypeHandle(frame.f_context.f_container));
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        bctx.typeMatrix.assign(getAddress(), m_nToValue,
            bctx.getArgumentType(m_nFromValue).getType());
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regFrom = bctx.loadArgument(code, m_nFromValue);

        bctx.loadCtx(code);
        if (regFrom.type().isJitInterface()) {
            code.checkcast(CD_nObj);
        }
        code.invokevirtual(CD_nObj, "$type", MethodTypeDesc.of(CD_nType, CD_Ctx));

        bctx.storeValue(code, m_nToValue, regFrom.type().getType());
        return -1;
    }
}