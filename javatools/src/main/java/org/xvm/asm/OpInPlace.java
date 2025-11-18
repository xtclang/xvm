package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeSystem;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for IP_ (in-place) op codes.
 *
 * Note: "property in-place" ops derive from {@link OpPropInPlace} and
 *       "property in-place assign" from {@link OpPropInPlaceAssign}.
 */
public abstract class OpInPlace
        extends Op {
    /**
     * Construct an "in-place" op for the passed target.
     *
     * @param argTarget  the target Argument
     */
    protected OpInPlace(Argument argTarget) {
        assert(!isAssignOp());

        m_argTarget = argTarget;
    }

    /**
     * Construct an "in-place and assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpInPlace(Argument argTarget, Argument argReturn) {
        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpInPlace(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTarget = readPackedInt(in);
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
            if (isAssignOp()) {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
            }
        }

        writePackedLong(out, m_nTarget);
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
            int nTarget = m_nTarget;
            if (nTarget >= 0) {
                // operation on a register
                if (frame.isDynamicVar(nTarget)) {
                    RefHandle hVar = frame.getDynamicVar(nTarget);
                    if (hVar == null) {
                        return R_REPEAT;
                    }

                    if (isAssignOp() && frame.isNextRegister(m_nRetValue)) {
                        frame.introduceRefTypeVar(nTarget);
                    }

                    return completeWithVar(frame, hVar);
                } else {
                    ObjectHandle hTarget = frame.getArgument(nTarget);

                    if (isAssignOp() && frame.isNextRegister(m_nRetValue)) {
                        frame.introduceVarCopy(m_nRetValue, nTarget);
                    }

                    return isDeferred(hTarget)
                            ? hTarget.proceed(frame, frameCaller ->
                                completeWithRegister(frameCaller, frameCaller.popStack()))
                            : completeWithRegister(frame, hTarget);
                }
            } else {
                // operation on a local property
                if (isAssignOp() && frame.isNextRegister(m_nRetValue)) {
                    frame.introduceVarCopy(m_nRetValue, nTarget);
                }

                PropertyConstant idProp = (PropertyConstant) frame.getConstant(nTarget);

                return completeWithProperty(frame, idProp);
            }
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int completeWithRegister(Frame frame, ObjectHandle hTarget) {
        throw new UnsupportedOperationException();
    }

    protected int completeWithVar(Frame frame, RefHandle hTarget) {
        throw new UnsupportedOperationException();
    }

    protected int completeWithProperty(Frame frame, PropertyConstant idProp) {
        throw new UnsupportedOperationException();
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
        if (isAssignOp()) {
            m_argReturn = registerArgument(m_argReturn, registry);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append(' ')
          .append(getTargetString());

        if (isAssignOp()) {
            sb.append(", ")
              .append(getReturnString());
        }

        return sb.toString();
    }

    protected String getTargetString() {
        return Argument.toIdString(m_argTarget, m_nTarget);
    }

    protected String getReturnString() {
        return Argument.toIdString(m_argReturn, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        int nTarget = m_nTarget;
        if (nTarget >= 0) {
            // operation on a register
            RegisterInfo reg = bctx.getRegisterInfo(nTarget);
            if (reg.cd().isPrimitive()) {
                assert reg.isSingle();

                buildPrimitiveLocal(code, reg);
                if (isAssignOp()) {
                    bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, reg.type()));
                }
            } else {
                // call the corresponding op method
                JitMethodDesc jmd = buildOpCallLocal(bctx, code, reg);
                if (isAssignOp()) {
                    bctx.assignReturns(code, jmd, 1, new int[] {m_nRetValue});
                }
            }
        } else {
            // operation on a local property
            PropertyConstant idProp   = (PropertyConstant) bctx.getConstant(nTarget);
            TypeConstant     typeProp = idProp.getType();
            if (typeProp.isPrimitive()) {
                buildPrimitiveProperty(bctx, code, idProp);
                if (isAssignOp()) {
                    bctx.ensureRegInfo(m_nRetValue, idProp.getType());
                }
            } else {
                // call the corresponding op method
                JitMethodDesc jmd = buildOpCallProperty(bctx, code);
                if (isAssignOp()) {
                    bctx.assignReturns(code, jmd, 1, new int[] {m_nRetValue});
                }
            }
        }
    }

    /**
     * Build the primitive local ops.
     *
     * In:  nothing on the Java stack
     * Out: the result on Java stack
     */
    protected void buildPrimitiveLocal(CodeBuilder code, RegisterInfo reg) {
        switch (reg.cd().descriptorString()) {
        case "I", "S", "B", "C", "Z":
            switch (getOpCode()) {
            case OP_IP_DEC:
                code.iinc(reg.slot(), -1);
                break;

            case OP_IP_INC:
                code.iinc(reg.slot(), +1);
                break;

            case OP_IP_DECA:
                code.iload(reg.slot())
                    .iinc(reg.slot(), -1); // leaves the old value on Java stack
                break;

            case OP_IP_INCA:
                code.iload(reg.slot())
                    .iinc(reg.slot(), +1);
                break;

            case OP_IP_DECB:
                code.iinc(reg.slot(), -1)
                    .iload(reg.slot());
                break;

            case OP_IP_INCB:
                code.iinc(reg.slot(), +1)
                    .iload(reg.slot());
                break;

            default:
                throw new IllegalStateException();
            }
            break;

        case "J":
            code.lload(reg.slot());
            switch (getOpCode()) {
            case OP_IP_DEC:
                code.lconst_1()
                    .lsub();
                break;

            case OP_IP_INC:
                code.lconst_1()
                    .ladd();
                break;

            case OP_IP_DECA:
                code.dup2()
                    .lconst_1()
                    .lsub();
                break;

            case OP_IP_INCA:
                code.dup2()
                    .lconst_1()
                    .ladd();
                break;

            case OP_IP_DECB:
                code.lconst_1()
                    .lsub()
                    .dup2();
                break;

            case OP_IP_INCB:
                code.lconst_1()
                    .ladd()
                    .dup2();
                break;

            default:
                throw new IllegalStateException();
            }
            code.lstore(reg.slot());
            break;

        case "F":
            code.fload(reg.slot());
            switch (getOpCode()) {
            case OP_IP_DEC:
                code.fconst_1()
                    .fsub();
                break;

            case OP_IP_INC:
                code.fconst_1()
                    .fadd();
                break;

            case OP_IP_DECA:
                code.dup2()
                    .fconst_1()
                    .fsub();
                break;

            case OP_IP_INCA:
                code.dup2()
                    .fconst_1()
                    .fadd();
                break;

            case OP_IP_DECB:
                code.fconst_1()
                    .fsub()
                    .dup2();
                break;

            case OP_IP_INCB:
                code.fconst_1()
                    .fadd()
                    .dup2();
                break;

            default:
                throw new IllegalStateException();
            }
            code.fstore(reg.slot());
            break;

        case "D":
            code.dload(reg.slot());
            switch (getOpCode()) {
            case OP_IP_DEC:
                code.dconst_1()
                    .dsub();
                break;

            case OP_IP_INC:
                code.dconst_1()
                    .dadd();
                break;

            case OP_IP_DECA:
                code.dup2()
                    .dconst_1()
                    .dsub();
                break;

            case OP_IP_INCA:
                code.dup2()
                    .dconst_1()
                    .dadd();
                break;

            case OP_IP_DECB:
                code.dconst_1()
                    .dsub()
                    .dup2();
                break;

            case OP_IP_INCB:
                code.dconst_1()
                    .dadd()
                    .dup2();
                break;

            default:
                throw new IllegalStateException();
            }
            code.dstore(reg.slot());
            break;

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Build the non-primitive type ops for a local variable.
     *
     * In:  nothing on the Java stack
     * Out: the result on Java stack
     */
    protected JitMethodDesc buildOpCallLocal(BuildContext bctx, CodeBuilder code, RegisterInfo reg) {
        String sName;
        String sOp;
        switch (getOpCode()) {
            case OP_IP_DEC  -> {sName = "prevValue";     sOp = null; }
            case OP_IP_INC  -> {sName = "nextValue";     sOp = null; }
            case OP_IP_DECA -> {sName = "postDecrement"; sOp = "#--";}
            case OP_IP_INCA -> {sName = "postIncrement"; sOp = "#++";}
            case OP_IP_DECB -> {sName = "preDecrement";  sOp = "--#";}
            case OP_IP_INCB -> {sName = "preIncrement";  sOp = "++#";}
            default         -> throw new IllegalStateException();
            }

        TypeConstant  type     = reg.type();
        MethodInfo    method   = type.ensureTypeInfo().findOpMethod(sName, sOp, 0);
        String        sJitName = method.ensureJitMethodName(bctx.typeSystem);
        JitMethodDesc jmd      = method.getJitDesc(bctx.typeSystem, type);

        assert !jmd.isOptimized;

        code.aload(reg.slot());
        bctx.loadCtx(code);
        code.invokevirtual(reg.cd(), sJitName, jmd.standardMD);
        return jmd;
    }

    /**
     * Build the primitive local property based ops.
     *
     * In:  nothing on the Java stack
     * Out: the result on Java stack
     */
    protected void buildPrimitiveProperty(BuildContext bctx, CodeBuilder code,
                                          PropertyConstant idProp) {
        TypeSystem    ts       = bctx.typeSystem;
        PropertyInfo  infoProp = idProp.getPropertyInfo();
        JitMethodDesc jmdGet   = infoProp.getGetterJitDesc(ts);
        JitMethodDesc jmdSet   = infoProp.getSetterJitDesc(ts);
        JitParamDesc  pd       = jmdGet.optimizedReturns[0];
        ClassDesc     cd       = pd.cd;

        assert jmdGet.isOptimized || jmdSet.isOptimized;

        MethodTypeDesc mdGet    = jmdGet.optimizedMD;
        MethodTypeDesc mdSet    = jmdSet.optimizedMD;
        String         sGetName = infoProp.ensureGetterJitMethodName(ts) + Builder.OPT;
        String         sSetName = infoProp.ensureSetterJitMethodName(ts) + Builder.OPT;

        RegisterInfo regTarget = bctx.loadThis(code);
        bctx.loadCtx(code);
        code.invokevirtual(regTarget.cd(), sGetName, mdGet);

        int op = getOpCode();
        switch (cd.descriptorString()) {
        case "I", "S", "B", "C", "Z":
            switch (op) {
            case OP_IP_DEC, OP_IP_INC:
                code.iconst_1();
                if (op == OP_IP_DEC) {
                    code.isub();
                } else {
                    code.iadd();
                }
                buildSetProperty(bctx, code, regTarget, cd, sSetName, mdSet);
                break;

            case OP_IP_DECA, OP_IP_INCA:
                int slotTmp = bctx.scope.allocateJavaSlot(TypeKind.INT);

                code.dup()
                    .istore(slotTmp) // store the original value
                    .iconst_1();
                if (op == OP_IP_DECA) {
                    code.isub();
                } else {
                    code.iadd();
                }
                buildSetProperty(bctx, code, regTarget, cd, sSetName, mdSet);
                code.iload(slotTmp); // load the original value on Java stack
                break;

            case OP_IP_DECB, OP_IP_INCB:
                code.iconst_1();
                if (op == OP_IP_DECB) {
                    code.isub();
                } else {
                    code.iadd();
                }
                code.dup();
                buildSetProperty(bctx, code, regTarget, cd, sSetName, mdSet);
                break;

            default:
                throw new IllegalStateException();
            }
            break;

        case "J":
            switch (op) {
            case OP_IP_DEC, OP_IP_INC:
                code.lconst_1();
                if (op == OP_IP_DEC) {
                    code.lsub();
                } else {
                    code.ladd();
                }
                buildSetProperty(bctx, code, regTarget, cd, sSetName, mdSet);
                break;

            case OP_IP_DECA, OP_IP_INCA:
                int slotTmp = bctx.scope.allocateJavaSlot(TypeKind.LONG);

                code.dup2()
                    .lstore(slotTmp) // store the original value
                    .lconst_1();
                if (op == OP_IP_DECA) {
                    code.lsub();
                } else {
                    code.ladd();
                }
                buildSetProperty(bctx, code, regTarget, cd, sSetName, mdSet);
                code.lload(slotTmp); // load the original value on Java stack
                break;

            case OP_IP_DECB, OP_IP_INCB:
                code.lconst_1();
                if (op == OP_IP_DECB) {
                    code.lsub();
                } else {
                    code.ladd();
                }
                code.dup2();
                buildSetProperty(bctx, code, regTarget, cd, sSetName, mdSet);
                break;

            default:
                throw new IllegalStateException();
            }
            break;

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * In: the new value on Java stack.
     */
    private void buildSetProperty(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget,
                                  ClassDesc cd, String sSetName, MethodTypeDesc mdSet) {
        int slotTmp = bctx.scope.allocateJavaSlot(cd);

        Builder.store(code, cd, slotTmp);
        bctx.loadThis(code);
        bctx.loadCtx(code);
        Builder.load(code, cd, slotTmp);
        code.invokevirtual(regTarget.cd(), sSetName, mdSet); // set the new value
    }

    /**
     * Build a non-primitive type ops for a property.
     *
     * In:  nothing on the Java stack
     * Out: the result on Java stack
     */
    protected JitMethodDesc buildOpCallProperty(BuildContext bctx, CodeBuilder code) {
        throw new UnsupportedOperationException();
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nTarget;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;
}
