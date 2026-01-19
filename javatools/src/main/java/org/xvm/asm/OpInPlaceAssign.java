package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for IP_ (in-place assign) op codes.
 */
public abstract class OpInPlaceAssign
        extends OpOptimized {
    /**
     * Construct an "in-place assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second Argument
     */
    protected OpInPlaceAssign(Argument argTarget, Argument argValue) {
        m_argTarget = argTarget;
        m_argValue = argValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpInPlaceAssign(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTarget = readPackedInt(in);
        m_nArgValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTarget != null) {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nArgValue = encodeArgument(m_argValue,  registry);
        }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nArgValue);
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            int nTarget = m_nTarget;
            if (nTarget >= 0) {
                // operation on a register
                if (frame.isDynamicVar(nTarget)) {
                    RefHandle hVar = frame.getDynamicVar(nTarget);
                    if (hVar == null) {
                        return R_REPEAT;
                    }

                    ObjectHandle hValue = frame.getArgument(m_nArgValue);

                    return isDeferred(hValue)
                            ? hValue.proceed(frame, frameCaller ->
                                completeWithVar(frameCaller, hVar, frameCaller.popStack()))
                            : completeWithVar(frame, hVar, hValue);
                } else {
                    ObjectHandle hTarget = frame.getArgument(nTarget);
                    ObjectHandle hValue  = frame.getArgument(m_nArgValue);

                    if (isDeferred(hTarget) || isDeferred(hValue)) {
                        ObjectHandle[] ahArg = new ObjectHandle[] {hTarget, hValue};
                        Frame.Continuation stepNext = frameCaller ->
                            completeWithRegister(frameCaller, ahArg[0], ahArg[1]);

                        return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                    }
                    return completeWithRegister(frame, hTarget, hValue);
                }
            } else {
                PropertyConstant idProp = frame.getConstant(nTarget, PropertyConstant.class);

                ObjectHandle hTarget = frame.getThis();
                ObjectHandle hValue  = frame.getArgument(m_nArgValue);

                return isDeferred(hValue)
                        ? hValue.proceed(frame, frameCaller ->
                            completeWithProperty(frameCaller, hTarget, idProp, frameCaller.popStack()))
                        : completeWithProperty(frame, hTarget, idProp, hValue);
            }
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regTarget = bctx.ensureRegister(code, m_nTarget);

        if (!regTarget.isSingle()) {
            throw new UnsupportedOperationException(toName(getOpCode()) + " operation on multi-slot");
        }

        TypeConstant typeResult;
        if (regTarget.cd().isPrimitive()) {
            typeResult = buildOptimizedBinary(bctx, code, regTarget, m_nArgValue);
        } else {
            typeResult = buildOpInvoke(bctx, code, regTarget);
        }
        bctx.storeValue(code, regTarget, typeResult);
    }

    /**
     * Invokes the operation method on the target register.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the target register
     */
    protected TypeConstant buildOpInvoke(BuildContext bctx,
                                         CodeBuilder  code,
                                         RegisterInfo regTarget) {
        TypeConstant  typeTarget = regTarget.type();
        MethodInfo    method     = findOpMethod(bctx, typeTarget);
        String        sJitName   = method.ensureJitMethodName(bctx.typeSystem);
        JitMethodDesc jmd        = method.getJitDesc(bctx.typeSystem, typeTarget);

        MethodTypeDesc md;
        if (jmd.isOptimized) {
            md        = jmd.optimizedMD;
            sJitName += Builder.OPT;
        } else {
            md = jmd.standardMD;
        }

        regTarget.load(code);
        bctx.loadCtx(code);
        bctx.loadArgument(code, m_nArgValue);
        code.invokevirtual(regTarget.cd(), sJitName, md);

        return method.getSignature().getRawReturns()[0]; // could differ from the target
    }

    /**
     * The completion of processing; m_nTarget >= 0.
     */
    protected int completeWithRegister(Frame frame, ObjectHandle hTarget, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * The completion of processing.
     */
    protected int completeWithVar(Frame frame, RefHandle hTarget, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * The completion of processing.
     */
    protected int completeWithProperty(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                       ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * Find the op method.
     */
    private MethodInfo findOpMethod(BuildContext bctx, TypeConstant typeTarget) {
        String sName;
        String sOp;
        switch (getOpCode()) {
            case OP_IP_ADD  -> {sName = "add";           sOp = "+";   }
            case OP_IP_SUB  -> {sName = "sub";           sOp = "-";   }
            case OP_IP_MUL  -> {sName = "mul";           sOp = "*";   }
            case OP_IP_DIV  -> {sName = "div";           sOp = "/";   }
            case OP_IP_MOD  -> {sName = "mod";           sOp = "%";   }
            case OP_IP_SHL  -> {sName = "shiftLeft";     sOp = "<<";  }
            case OP_IP_SHR  -> {sName = "shiftRight";    sOp = ">>";  }
            case OP_IP_USHR -> {sName = "shiftAllRight"; sOp = ">>";  }
            case OP_IP_AND  -> {sName = "and";           sOp = "&";   }
            case OP_IP_OR   -> {sName = "or";            sOp = "|";   }
            case OP_IP_XOR  -> {sName = "xor";           sOp = "^";   }

            default -> throw new UnsupportedOperationException(toName(getOpCode()));
        }

        TypeConstant  typeArg  = bctx.getArgumentType(m_nArgValue);
        MethodInfo    method   = typeTarget.ensureTypeInfo().findOpMethod(sName, sOp, typeArg);
        return method;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_argValue = registerArgument(m_argValue, registry);
    }

    @Override
    public String toString() {
        return super.toString()
                + ", " + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argValue, m_nArgValue);
    }

    protected int m_nTarget;
    protected int m_nArgValue;

    private Argument m_argTarget;
    private Argument m_argValue;
}
