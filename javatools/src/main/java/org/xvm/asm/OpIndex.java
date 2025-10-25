package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.Utils;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_xObj;

import static org.xvm.javajit.JitFlavor.MultiSlotPrimitive;
import static org.xvm.javajit.JitFlavor.Primitive;
import static org.xvm.javajit.JitFlavor.Specific;
import static org.xvm.javajit.JitFlavor.Widened;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for I_ (index based) and IIP_ (index based in-place) op codes.
 */
public abstract class OpIndex
        extends Op {
    /**
     * Construct an "index based" op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     */
    protected OpIndex(Argument argTarget, Argument argIndex) {
        assert(!isAssignOp());

        m_argTarget = argTarget;
        m_argIndex  = argIndex;
    }

    /**
     * Construct an "in-place and assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpIndex(Argument argTarget, Argument argIndex, Argument argReturn) {
        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argIndex  = argIndex;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpIndex(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTarget = readPackedInt(in);
        m_nIndex  = readPackedInt(in);
        if (isAssignOp()) {
            m_nRetValue = readPackedInt(in);
        }
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTarget != null) {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nIndex  = encodeArgument(m_argIndex, registry);
            if (isAssignOp()) {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
            }
        }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nIndex);
        if (isAssignOp()) {
            writePackedLong(out, m_nRetValue);
        }
    }

    /**
     * A "virtual constant" indicating whether or not this op is an assigning one.
     *
     * @return true iff the op is an assigning one
     */
    protected boolean isAssignOp() {
        // majority of the ops are assigning; let's default to that
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nIndex}, 2);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return complete(frame, ahArg[0], ahArg[1]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    /**
     * Complete the op processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hIndex) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieve cached call chain.
     */
    protected CallChain getOpChain(Frame frame, TypeConstant typeTarget) {
        ServiceContext ctx   = frame.f_context;
        CallChain      chain = (CallChain) ctx.getOpInfo(this, Category.Chain);
        if (chain != null) {
            TypeConstant typePrevTarget = (TypeConstant) ctx.getOpInfo(this, Category.Type);
            if (typeTarget.equals(typePrevTarget)) {
                return chain;
            }
        }
        return null;
    }

    /**
     * Cache the specified call chain for the given target.
     */
    protected void saveOpChain(Frame frame, TypeConstant typeTarget, CallChain chain) {
        ServiceContext ctx = frame.f_context;
        ctx.setOpInfo(this, Category.Chain, chain);
        ctx.setOpInfo(this, Category.Type, typeTarget);
    }

    @Override
    public void resetSimulation() {
        if (isAssignOp()) {
            resetRegister(m_argReturn);
        }
    }

    @Override
    public void simulate(Scope scope) {
        if (isAssignOp()) {
            checkNextRegister(scope, m_argReturn, m_nRetValue);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_argIndex = registerArgument(m_argIndex, registry);
        if (isAssignOp()) {
            m_argReturn = registerArgument(m_argReturn, registry);
        }
    }

    @Override
    public String toString() {
        return super.toString()
                + ' '  + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argIndex,  m_nIndex)
                + ", " + Argument.toIdString(m_argReturn, m_nRetValue);
    }


    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        TypeSystem   ts     = bctx.typeSystem;
        Slot         slot   = bctx.loadArgument(code, m_nTarget);
        TypeConstant type   = slot.type();
        TypeConstant typeEl = type.getParamType(0);

        if (type.isArray()) {
            if (typeEl.isPrimitive()) {
                ClassDesc cdArray = type.ensureClassDesc(ts);
                ClassDesc cdEl    = JitTypeDesc.getPrimitiveClass(typeEl);

                bctx.loadCtx(code);
                bctx.loadArgument(code, m_nIndex);

                // we assume that all customized implementations have the same names
                switch (getOpCode()) {
                    case OP_I_GET ->
                        code.invokevirtual(cdArray, "getElement$pi",
                            MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));

                    case OP_I_SET -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "setElement$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                    }

                    // IntNumber and Char only
                    case OP_IIP_INC -> {
                        code.invokevirtual(cdArray, "preInc$pi",
                                MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));
                        Builder.pop(code, cdEl); // ignore the return value
                    }

                    case OP_IIP_DEC -> {
                        code.invokevirtual(cdArray, "preDec$pi",
                                MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));
                        Builder.pop(code, cdEl); // ignore the return value
                    }

                    case OP_IIP_INCA ->
                        code.invokevirtual(cdArray, "postInc$pi",
                                MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));

                    case OP_IIP_DECA ->
                        code.invokevirtual(cdArray, "postDec$pi",
                                MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));

                    case OP_IIP_INCB ->
                        code.invokevirtual(cdArray, "preInc$pi",
                                MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));

                    case OP_IIP_DECB ->
                        code.invokevirtual(cdArray, "preDec$pi",
                                MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));

                    case OP_IIP_ADD -> {
                        // @Op(+) Char add(Int n)
                        ClassDesc cdArg = typeEl.equals(ts.pool().typeChar())
                            ? CD_long
                            : cdEl;
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "addInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdArg));
                        }

                    case OP_IIP_SUB -> {
                        // @Op(+) Char add(Int n)
                        ClassDesc cdArg = typeEl.equals(ts.pool().typeChar())
                            ? CD_long
                            : cdEl;
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "subInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdArg));
                        }

                    // IntNumber only
                    case OP_IIP_MUL -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "mulInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                        }

                    case OP_IIP_DIV -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "divInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                    }

                    case OP_IIP_MOD -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "modInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                    }

                    case OP_IIP_SHL -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "shlInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, CD_long));
                    }
                    case OP_IIP_SHR -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "shrInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, CD_long));
                    }
                    case OP_IIP_USHR -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "shrAllInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, CD_long));
                    }

                    // IntNumber and Boolean
                    case OP_IIP_AND -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "andInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                    }
                    case OP_IIP_OR -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "orInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                    }
                    case OP_IIP_XOR -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "xorInPlace$pi",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, cdEl));
                    }

                    default -> throw new UnsupportedOperationException(toName(getOpCode()));
                }
            } else {
                ClassDesc cdArray = Builder.CD_xArrayObj;
                bctx.loadCtx(code);
                bctx.loadArgument(code, m_nIndex);
                switch (getOpCode()) {
                    case OP_I_GET ->
                        code.invokevirtual(cdArray, "getElement$p",
                            MethodTypeDesc.of(CD_xObj, CD_Ctx, CD_long));

                    case OP_I_SET -> {
                        bctx.loadArgument(code, getValueIndex());
                        code.invokevirtual(cdArray, "setElement$p",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, CD_xObj));
                    }

                    default -> throw new UnsupportedOperationException(toName(getOpCode()));
                }
            }
        } else {
            String sName;
            String sOp;
            switch (getOpCode()) {
                case OP_I_GET    -> {sName = "getElement";    sOp = "[]"; }
                case OP_I_SET    -> {sName = "setElement";    sOp = "[]=";}
                case OP_IIP_INC  -> {sName = "nextValue";     sOp = "";   }
                case OP_IIP_DEC  -> {sName = "prevValue";     sOp = "";   }
                case OP_IIP_INCA -> {sName = "";              sOp = "x++";}
                case OP_IIP_DECA -> {sName = "";              sOp = "x--";}
                case OP_IIP_INCB -> {sName = "";              sOp = "++x";}
                case OP_IIP_DECB -> {sName = "";              sOp = "--x";}
                case OP_IIP_ADD  -> {sName = "add";           sOp = "+";  }
                case OP_IIP_SUB  -> {sName = "sub";           sOp = "-";  }
                case OP_IIP_MUL  -> {sName = "mul";           sOp = "*";  }
                case OP_IIP_DIV  -> {sName = "div";           sOp = "/";  }
                case OP_IIP_MOD  -> {sName = "mod";           sOp = "%";  }
                case OP_IIP_SHL  -> {sName = "shiftLeft";     sOp = "<<"; }
                case OP_IIP_SHR  -> {sName = "shiftRight";    sOp = ">>"; }
                case OP_IIP_USHR -> {sName = "shiftAllRight"; sOp = ">>"; }
                case OP_IIP_AND  -> {sName = "and";           sOp = "&";  }
                case OP_IIP_OR   -> {sName = "or";            sOp = "|";  }
                case OP_IIP_XOR  -> {sName = "xor";           sOp = "^";  }
                default          -> throw new UnsupportedOperationException(toName(getOpCode()));
            }
            MethodInfo    method   = type.ensureTypeInfo().findOpMethod(sName, sOp, 1);
            String        sJitName = method.getJitIdentity().ensureJitMethodName(ts);
            throw new UnsupportedOperationException("TODO");
        }

        if (isAssignOp()) {
            JitParams      result      = computeJitParams(ts, typeEl);
            JitParamDesc[] apdOptParam = result.apdOptParam();

            JitMethodDesc jmd = new JitMethodDesc(
                result.apdStdParam(), JitParamDesc.NONE,
                apdOptParam, apdOptParam == null ? null : JitParamDesc.NONE);

            bctx.assignReturns(code, jmd, 1, new int[] {m_nRetValue});
        }
    }

    private JitParams computeJitParams(TypeSystem ts, TypeConstant type) {
        JitParamDesc[] apdOptParam = null;
        JitParamDesc[] apdStdParam;
        ClassDesc cd;

        if ((cd = JitTypeDesc.getPrimitiveClass(type)) != null) {
            ClassDesc cdStd  = ClassDesc.of(ts.ensureJitClassName(type));

            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Specific, cdStd, 0, 0, false)};
            apdOptParam = new JitParamDesc[] {
                new JitParamDesc(type, Primitive, cd, 0, 0, false)};
        } else if ((cd = JitTypeDesc.getMultiSlotPrimitiveClass(type)) != null) {
            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Widened, cd, 0, 0, false)};
            apdOptParam = new JitParamDesc[] {
                new JitParamDesc(type, MultiSlotPrimitive, cd, 0, 0, false),
                new JitParamDesc(type, MultiSlotPrimitive, CD_boolean, 0, 1, true)
            };
        } else if ((cd = JitTypeDesc.getWidenedClass(type)) != null) {
            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Widened, cd, 0, 0, false)};
        } else {
            assert type.isSingleUnderlyingClass(true);

            cd = ClassDesc.of(ts.ensureJitClassName(type));
            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Specific, cd, 0, 0, false)};
        }
        return new JitParams(apdStdParam, apdOptParam);
    }

    private record JitParams(JitParamDesc[] apdStdParam, JitParamDesc[] apdOptParam) {}

    /**
     * @return the index of the argument value for corresponding ops
     */
    protected int getValueIndex() {
        throw new UnsupportedOperationException("TODO " + getClass().getName());
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nTarget;
    protected int m_nIndex;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argIndex;
    private Argument m_argReturn;

    // categories for cached info
    enum Category {Chain, Type}
}