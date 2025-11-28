package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.List;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.DoubleSlot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.javajit.TypeSystem;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static java.lang.constant.ConstantDescs.CD_long;
import static org.xvm.javajit.Builder.CD_Container;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_Ordered;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.Builder.CD_nType;
import static org.xvm.javajit.Builder.MD_TypeIsA;
import static org.xvm.javajit.Builder.MD_xvmType;

import static org.xvm.javajit.Builder.OPT;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for conditional jump (JMP_*) op-codes.
 */
public abstract class OpCondJump
        extends Op {
    /**
     * Construct a unary conditional JMP_ op.
     *
     * @param arg  a value Argument
     * @param op   the op to jump to
     */
    protected OpCondJump(Argument arg, Op op) {
        assert !hasSecondArgument() && !isBinaryOp();
        m_argVal = arg;
        m_opDest = op;
    }

    /**
     * Construct a binary conditional JMP_ op.
     *
     * @param arg   a value Argument
     * @param arg2  a second value Argument
     * @param op    the op to jump to
     */
    protected OpCondJump(Argument arg, Argument arg2, Op op) {
        assert hasSecondArgument() && !isBinaryOp();
        m_argVal  = arg;
        m_argVal2 = arg2;
        m_opDest  = op;
    }

    /**
     * Construct a binary conditional JMP_ op.
     *
     * @param type  the compile-time type
     * @param arg   a value Argument
     * @param arg2  a second value Argument
     * @param op    the op to jump to
     */
    protected OpCondJump(TypeConstant type, Argument arg, Argument arg2, Op op) {
        assert isBinaryOp();
        m_typeCommon = type;
        m_argVal     = arg;
        m_argVal2    = arg2;
        m_opDest     = op;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpCondJump(DataInput in, Constant[] aconst)
            throws IOException {
        if (isBinaryOp()) {
            m_nType = readPackedInt(in);
        }
        m_nArg = readPackedInt(in);
        if (hasSecondArgument()) {
            m_nArg2 = readPackedInt(in);
        }
        m_ofJmp = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_typeCommon != null) {
            m_nType = encodeArgument(m_typeCommon, registry);
            m_typeCommon = null;
        }
        if (m_argVal != null) {
            m_nArg = encodeArgument(m_argVal, registry);
        }
        if (m_argVal2 != null) {
            m_nArg2 = encodeArgument(m_argVal2, registry);
        }

        if (isBinaryOp()) {
            writePackedLong(out, m_nType);
        }
        writePackedLong(out, m_nArg);
        if (hasSecondArgument()) {
            writePackedLong(out, m_nArg2);
        }
        writePackedLong(out, m_ofJmp);
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        if (m_opDest == null) {
            m_ofJmp  = adjustRelativeAddress(aop, m_ofJmp);
            m_opDest = aop[getAddress() + m_ofJmp];
        } else {
            m_ofJmp = calcRelativeAddress(m_opDest);
        }
        m_cExits = calcExits(m_opDest);
    }

    /**
     * @return true iff the op is a binary operator
     */
    protected boolean isBinaryOp() {
        return false;
    }

    /**
     * @return true iff the op has two arguments
     */
    protected boolean hasSecondArgument() {
        return isBinaryOp();
    }

    @Override
    public int process(Frame frame, int iPC) {
        return isBinaryOp() ? processBinaryOp(frame, iPC) : processUnaryOp(frame, iPC);
    }

    protected int processUnaryOp(Frame frame, int iPC) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nArg);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        completeUnaryOp(frameCaller, iPC, frameCaller.popStack()))
                    : completeUnaryOp(frame, iPC, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int processBinaryOp(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg      = frame.getArguments(new int[]{m_nArg, m_nArg2}, 2);
            TypeConstant   typeCommon = calculateCommonType(frame);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, iPC, typeCommon, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return completeBinaryOp(frame, iPC, typeCommon, ahArg[0], ahArg[1]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected TypeConstant calculateCommonType(Frame frame) {
        TypeConstant typeCommon = m_typeCommon;
        if (typeCommon == null) {
            m_typeCommon = typeCommon = (TypeConstant) frame.getConstant(m_nType);
        }
        return frame.resolveType(typeCommon);
    }

    /**
     * A completion of a unary op; must be overridden by all binary ops.
     */
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * A completion of a binary op; must be overridden by all binary ops.
     */
    protected int completeBinaryOp(Frame frame, int iPC, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);

        m_opDest = findDestinationOp(aop, m_ofJmp);
        m_ofJmp  = calcRelativeAddress(m_opDest);
    }

    @Override
    public boolean checkRedundant(Op[] aop) {
        if (m_ofJmp == 1) {
            markRedundant();
            return true;
        }

        return false;
    }

    @Override
    public boolean branches(Op[] aop, List<Integer> list) {
        list.add(getRelativeAddress());
        return true;
    }

    /**
     * @return the number of instructions to jump (may be negative)
     */
    public int getRelativeAddress() {
        return m_ofJmp;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        if (isBinaryOp()) {
            m_typeCommon = (TypeConstant) registerArgument(m_typeCommon, registry);
        }
        m_argVal = registerArgument(m_argVal, registry);
        if (hasSecondArgument()) {
            m_argVal2 = registerArgument(m_argVal2, registry);
        }
    }

    /**
     * @return a String that identifies the argument, for debugging purposes
     */
    protected String getArgDesc() {
        return Argument.toIdString(m_argVal, m_nArg);
    }

    /**
     * @return a String that identifies the second argument, for debugging purposes
     */
    protected String getArg2Desc() {
        assert hasSecondArgument();
        return Argument.toIdString(m_argVal2, m_nArg2);
    }

    /**
     * @return a String to use for debugging to denote the destination of the jump
     */
    protected String getLabelDesc() {
        if (m_opDest instanceof org.xvm.asm.op.Label label) {
            return label.getName();
        } else if (m_ofJmp != 0) {
            return (m_ofJmp > 0 ? "+" : "") + m_ofJmp;
        } else if (m_opDest != null) {
            return "-> " + m_opDest;
        } else {
            return "???";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toName(getOpCode()))
          .append(' ');

        if (isBinaryOp()) {
           sb.append(Argument.toIdString(m_typeCommon, m_nType))
             .append(", ");
        }

        sb.append(getArgDesc());

        if (hasSecondArgument()) {
            sb.append(", ")
              .append(getArg2Desc());
        }

        sb.append(", ")
          .append(getLabelDesc());

        return sb.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        if (isBinaryOp()) {
            buildBinary(bctx, code);
        } else {
            buildUnary(bctx, code);
        }
    }

    protected void buildBinary(BuildContext bctx, CodeBuilder code) {
        // this is very similar to OpTest logic
        TypeConstant type1       = bctx.getArgumentType(m_nArg);
        TypeConstant type2       = bctx.getArgumentType(m_nArg2);
        TypeConstant typeCommon  = selectCommonType(type1, type2, ErrorListener.BLACKHOLE);
        TypeConstant typeCompare = bctx.getType(m_nType); // TODO: can we get rid of it?

        if (typeCommon.equals(typeCompare)) {
            assembleUniformJump(bctx, code, typeCommon);
        } else {
            // the compiler allows a comparison of union types
            assert switch (getOpCode()) {
                case OP_JMP_EQ, OP_JMP_NEQ -> true;
                default -> false;
            };

            // consider the following scenario:
            //      boolean test(T1|T2 o1, T2 o2) = (o1 == o2);
            // the common type is T2, and we need to add a runtime type check for "o1" or "o2"
            if (!type1.isA(typeCompare)) {
                RegisterInfo reg1 = bctx.ensureRegister(code, m_nArg);
                if (!reg1.isSingle()) {
                    throw new UnsupportedOperationException("Handle MultiSlotPrimitive");
                }
                assembleExpandedJump(bctx, code, reg1, typeCompare);
            } else {
                assert !type2.isA(typeCompare);

                RegisterInfo reg2 = bctx.ensureRegister(code, m_nArg2);
                if (!reg2.isSingle()) {
                    throw new UnsupportedOperationException("Handle MultiSlotPrimitive");
                }
                assembleExpandedJump(bctx, code, reg2, typeCompare);
            }
        }
    }

    /**
     * Assemble the test starting with a type check for the specified argument.
     */
    private void assembleExpandedJump(BuildContext bctx, CodeBuilder code,
                                      RegisterInfo regTest, TypeConstant typeCompare) {
        assert !regTest.cd().isPrimitive();

        Label labelEnd = code.newLabel();
        int   nAddr    = getAddress();

        // o1.$xvmType(ctx).isA(typeCompare)
        code.aload(regTest.slot());
        bctx.loadCtx(code);
        code.invokevirtual(CD_nObj, "$xvmType", MD_xvmType);
        Builder.loadTypeConstant(code, bctx.typeSystem, typeCompare);
        code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);

        if (getOpCode() == OP_JMP_EQ) {
            code.ifeq(labelEnd); // the values cannot be equal - proceed
        } else {
            Label labelJump = bctx.ensureLabel(code, getAddress() + m_ofJmp);
            code.ifeq(labelJump); // the values cannot be equal - jump
        }
        // TODO: narrow
        assembleUniformJump(bctx, code, typeCompare);
        code.labelBinding(labelEnd);
    }


    /**
     * Generate the jump for arguments of the same type.
     */
    private void assembleUniformJump(BuildContext bctx, CodeBuilder code, TypeConstant typeCommon) {
        ClassDesc cdCommon = JitTypeDesc.getJitClass(bctx.typeSystem, typeCommon);
        Label     lblJump  = bctx.ensureLabel(code, getAddress() + m_ofJmp);

        if (cdCommon.isPrimitive()) {
            bctx.loadArgument(code, m_nArg);
            bctx.loadArgument(code, m_nArg2);

            String desc = cdCommon.descriptorString();
            switch (desc) {
            case "I", "S", "B", "C", "Z":
                switch (getOpCode()) {
                    case OP_JMP_EQ  -> code.if_icmpeq(lblJump);
                    case OP_JMP_NEQ -> code.if_icmpne(lblJump);
                    case OP_JMP_GT  -> code.if_icmpgt(lblJump);
                    case OP_JMP_GTE -> code.if_icmpge(lblJump);
                    case OP_JMP_LT  -> code.if_icmplt(lblJump);
                    case OP_JMP_LTE -> code.if_icmple(lblJump);
                    default         -> throw new IllegalStateException();
                }
                break;

            case "J", "F", "D":
                switch (desc) {
                    case "J" -> code.lcmp();
                    case "F" -> code.fcmpl(); // REVIEW CP: fcmpl vs fcmpg?
                    case "D" -> code.dcmpl(); // REVIEW CP: ditto
                }
                generateCmpJump(code, lblJump);
                break;

            default:
                throw new IllegalStateException();
            }
        } else if (typeCommon.isEnum()) {
            // strictly speaking, this code is not needed since the generic algorithm covers the
            // enums as well, but this path is slightly more optimal
            bctx.loadArgument(code, m_nArg2);
            bctx.loadCtx(code);
            code.invokevirtual(cdCommon, "ordinal$get$p", MethodTypeDesc.of(CD_long, CD_Ctx));
            int slotOrd2 = bctx.storeTempValue(code, CD_long);
            bctx.loadArgument(code, m_nArg2);
            bctx.loadCtx(code);
            code.invokevirtual(cdCommon, "ordinal$get$p", MethodTypeDesc.of(CD_long, CD_Ctx))
                .lload(slotOrd2)
                .lcmp();
            generateCmpJump(code, lblJump);
        } else {
            //  TODO: we assume typeCommon is a terminal type; we need to move some of this logic to
            //        the TypeConstant and override it for relational types
            assert typeCommon.isSingleUnderlyingClass(true);

            TypeSystem ts   = bctx.typeSystem;
            ConstantPool pool = ts.pool();
            int          nOp  = getOpCode();
            SignatureConstant sig = switch (nOp) {
                case OP_JMP_EQ, OP_JMP_NEQ                        -> pool.sigEquals();
                case OP_JMP_GT, OP_JMP_GTE, OP_JMP_LT, OP_JMP_LTE -> pool.sigCompare();
                default                                           -> throw new IllegalStateException();
            };
            MethodInfo    method   = typeCommon.ensureTypeInfo().getMethodBySignature(sig);
            ClassDesc     cd       = method.getJitIdentity().getNamespace().ensureClassDesc(ts);
            JitMethodDesc jmd      = method.getJitDesc(ts, typeCommon);
            String        sJitName = method.ensureJitMethodName(ts);

            bctx.loadCtx(code);
            switch (nOp) {
            case OP_JMP_EQ, OP_JMP_NEQ:
                assert jmd.isOptimized;
                // Boolean equals(Ctx,xType,xObj,xObj)
                Builder.loadType(code, ts, typeCommon);
                bctx.loadArgument(code, m_nArg);
                bctx.loadArgument(code, m_nArg2);
                code.invokestatic(cd, sJitName+OPT,
                        MethodTypeDesc.of(CD_boolean, CD_Ctx, CD_nType, cd, cd));
                generateCmpJump(code, lblJump);
                break;

            case OP_JMP_GT, OP_JMP_GTE, OP_JMP_LT, OP_JMP_LTE:
                // Ordered compare(Ctx,Type,xObj,xObj)
                Builder.loadType(code, ts, typeCommon);
                bctx.loadArgument(code, m_nArg);
                bctx.loadArgument(code, m_nArg2);
                code.invokestatic(cd, sJitName, MethodTypeDesc.of(CD_Ordered, CD_Ctx, CD_nType, cd, cd));
                generateOrderedJump(bctx, code, lblJump);
            }
        }
    }

    /**
     * Generate a jump opcode for a "lcmp" op result on the Java stack.
     */
    private void generateCmpJump(CodeBuilder code, Label lblJump) {
        switch (getOpCode()) {
            case OP_JMP_EQ  -> code.ifeq(lblJump);
            case OP_JMP_NEQ -> code.ifne(lblJump);
            case OP_JMP_GT  -> code.ifgt(lblJump);
            case OP_JMP_GTE -> code.ifge(lblJump);
            case OP_JMP_LT  -> code.iflt(lblJump);
            case OP_JMP_LTE -> code.ifle(lblJump);
            default -> throw new IllegalStateException();
        }
    }

    /**
     * Generate a jump opcode for an "Ordered" result on the Java stack.
     */
    private void generateOrderedJump(BuildContext bctx, CodeBuilder code, Label lblJump) {
        ConstantPool pool = bctx.pool();
        switch (getOpCode()) {
            case OP_JMP_GT -> {
                bctx.loadConstant(code, pool.valGreater());
                code.if_acmpeq(lblJump);
            }
            case OP_JMP_GTE -> {
                bctx.loadConstant(code, pool.valLesser());
                code.if_acmpne(lblJump);
            }
            case OP_JMP_LT -> {
                bctx.loadConstant(code, pool.valLesser());
                code.if_acmpeq(lblJump);
            }
            case OP_JMP_LTE -> {
                bctx.loadConstant(code, pool.valGreater());
                code.if_acmpne(lblJump);
            }
            default -> throw new IllegalStateException();
        }
    }

    protected void buildUnary(BuildContext bctx, CodeBuilder code) {
        Label lblJump = bctx.ensureLabel(code, getAddress() + m_ofJmp);
        int   op      = getOpCode();

        switch (op) {
        case OP_JMP_COND, OP_JMP_NCOND:
            bctx.loadCtx(code);
            code.getfield(CD_Ctx, "container", CD_Container);
            bctx.loadArgument(code, m_nArg);
            code.invokevirtual(CD_Container, "isSpecified",
                               MethodTypeDesc.of(CD_boolean, CD_JavaString));
            if (op == OP_JMP_COND) {
                code.ifne(lblJump);
            } else {
                code.ifeq(lblJump);
            }
            return;

        case OP_JMP_TYPE, OP_JMP_NTYPE:
            buildTypeJump(bctx, code);
            return;
        }

        RegisterInfo reg = bctx.loadArgument(code, m_nArg);
        ClassDesc    cd  = reg.cd();
        if (cd.isPrimitive()) {
            if (reg instanceof DoubleSlot doubleSlot) {
                assert doubleSlot.flavor() == JitFlavor.MultiSlotPrimitive;
                code.iload(doubleSlot.extSlot()); // boolean Null indication
                switch (op) {
                    case OP_JMP_NULL  -> code.ifne(lblJump);
                    case OP_JMP_NNULL -> code.ifeq(lblJump);
                    default           -> throw new IllegalStateException();
                }
                return;
            }
            Builder.defaultLoad(code, cd);

            String desc = cd.descriptorString();
            switch (desc) {
            case "I", "S", "B", "C", "Z":
                switch (getOpCode()) {
                    case OP_JMP_ZERO  -> code.if_icmpeq(lblJump);
                    case OP_JMP_NZERO -> code.if_icmpne(lblJump);
                    default           -> throw new IllegalStateException();
                }
                break;

            case "J", "F", "D":
                switch (desc) {
                    case "J" -> code.lcmp();
                    case "F" -> code.fcmpl(); // REVIEW CP: fcmpl vs fcmpg?
                    case "D" -> code.dcmpl(); // REVIEW CP: ditto
                }
                switch (op) {
                    case OP_JMP_ZERO  -> code.ifeq(lblJump);
                    case OP_JMP_NZERO -> code.ifne(lblJump);
                    default -> throw new IllegalStateException();
                }
                break;

            default:
                throw new IllegalStateException();
            }
        } else {
            switch (op) {
            case OP_JMP_NULL, OP_JMP_NNULL:
                Builder.loadNull(code);
                if (op == OP_JMP_NULL) {
                    code.if_acmpeq(lblJump);
                } else {
                    code.if_acmpne(lblJump);
                }
                break;

            default:
                throw new IllegalStateException();
            }
        }
    }

    private void buildTypeJump(BuildContext bctx, CodeBuilder code) {
        int   nAddrThis = getAddress();
        int   nAddrJump = nAddrThis + m_ofJmp;
        Label lblJump   = bctx.ensureLabel(code, nAddrJump);

        // this logic is almost identical to OpTest.buildTypeCheck();
        TypeConstant typeTarget = bctx.getArgumentType(m_nArg);
        if (m_nArg2 <= CONSTANT_OFFSET) {
            TypeConstant typeTest = bctx.getArgumentType(m_nArg2);
            assert typeTest.isTypeOfType();
            typeTest = typeTest.getParamType(0);

            if (typeTarget.isPrimitive()) {
                // we can statically compute the result, which most probably means that a formal
                // type was narrowed for a particular class flavor and the corresponding code
                // can be safely eliminated
                boolean isA = typeTarget.isA(typeTest);
                if ((getOpCode() == OP_JMP_TYPE) == isA) {
                    bctx.markDeadCode(nAddrThis + 1, nAddrJump);
                } else {
                    bctx.markDeadCode(nAddrJump, -1);
                }
                return;
            } else {
                RegisterInfo regTarget = bctx.loadArgument(code, m_nArg);
                bctx.loadCtx(code);
                code.invokevirtual(regTarget.cd(), "$xvmType", MD_xvmType); // target type
                Builder.loadTypeConstant(code, bctx.typeSystem, typeTest);  // test type
            }
            if (getOpCode() == OP_JMP_TYPE) {
                bctx.narrowTarget(code, m_nArg, nAddrJump, -1, typeTest);
            } else {
                bctx.narrowTarget(code, m_nArg, nAddrThis + 1, nAddrJump, typeTest);
            }
        } else {
            // dynamic types
            RegisterInfo regType = bctx.loadArgument(code, m_nArg2); // xType
            assert regType.type().isTypeOfType();
            code.getfield(CD_nType, "$type", CD_TypeConstant);
        }

        code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);

        if (getOpCode() == OP_JMP_TYPE) {
            code.ifne(lblJump);
        } else { // OP_JMP_NTYPE
            code.ifeq(lblJump);
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nType;
    protected int m_nArg;
    protected int m_nArg2;
    protected int m_ofJmp;

    protected TypeConstant m_typeCommon; // the type to use for the comparison
    private   Argument     m_argVal;
    private   Argument     m_argVal2;
    private   Op           m_opDest;

    // number of exits to simulate on the jump
    protected transient int m_cExits;
}
