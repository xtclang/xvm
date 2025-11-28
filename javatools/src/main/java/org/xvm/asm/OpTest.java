package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
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

import static org.xvm.javajit.Builder.CD_Ctx;
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
 * Base class for test (IS_*) op-codes.
 */
public abstract class OpTest
        extends Op {
    /**
     * Construct a unary IS_ op.
     *
     * @param arg        the value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(Argument arg, Argument argReturn) {
        assert !isBinaryOp() && !hasSecondArgument();
        m_argVal1   = arg;
        m_argReturn = argReturn;
    }

    /**
     * Construct a two argument IS_ op.
     *
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(Argument arg1, Argument arg2, Argument argReturn) {
        assert hasSecondArgument() && !isBinaryOp();
        m_argVal1   = arg1;
        m_argVal2   = arg2;
        m_argReturn = argReturn;
    }

    /**
     * Construct a binary IS_ op.
     *
     * @param type       the compile-time type
     * @param arg1       the first value Argument
     * @param arg2       the second value Argument
     * @param argReturn  the location to store the test result
     */
    protected OpTest(TypeConstant type, Argument arg1, Argument arg2, Argument argReturn) {
        assert isBinaryOp();
        m_typeCommon = type;
        m_argVal1    = arg1;
        m_argVal2    = arg2;
        m_argReturn  = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpTest(DataInput in, Constant[] aconst)
            throws IOException {
        if (isBinaryOp()) {
            m_nType = readPackedInt(in);
        }
        m_nValue1 = readPackedInt(in);
        if (hasSecondArgument()) {
            m_nValue2 = readPackedInt(in);
        }
        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_typeCommon != null) {
            // encode the common type and discard it to be recalculated using the correct pool
            m_nType      = encodeArgument(m_typeCommon, registry);
            m_typeCommon = null;
        }
        if (m_argVal1 != null) {
            m_nValue1 = encodeArgument(m_argVal1, registry);
        }
        if (m_argVal2 != null) {
            m_nValue2 = encodeArgument(m_argVal2, registry);
        }
        if (m_argReturn != null) {
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        if (isBinaryOp()) {
            writePackedLong(out, m_nType);
        }
        writePackedLong(out, m_nValue1);
        if (hasSecondArgument()) {
            writePackedLong(out, m_nValue2);
        }
        writePackedLong(out, m_nRetValue);
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
        if (frame.isNextRegister(m_nRetValue)) {
            frame.introduceResolvedVar(m_nRetValue, getResultType(frame));
        }
        return isBinaryOp() ? processBinaryOp(frame) : processUnaryOp(frame);
    }

    protected int processUnaryOp(Frame frame) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nValue1);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        completeUnaryOp(frameCaller, frameCaller.popStack()))
                    : completeUnaryOp(frame, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int processBinaryOp(Frame frame) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[]{m_nValue1, m_nValue2}, 2);

            TypeConstant typeCommon = calculateCommonType(frame);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, typeCommon, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return completeBinaryOp(frame, typeCommon, ahArg[0], ahArg[1]);
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
    protected int completeUnaryOp(Frame frame, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * A completion of a binary op; must be overridden by all binary ops.
     */
    protected int completeBinaryOp(Frame frame, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the result type for this Op
     */
    protected TypeConstant getResultType(Frame frame) {
        return frame.poolContext().typeBoolean();
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        if (isBinaryOp()) {
            m_typeCommon = (TypeConstant) registerArgument(m_typeCommon, registry);
        }
        m_argVal1 = registerArgument(m_argVal1, registry);
        if (hasSecondArgument()) {
            m_argVal2 = registerArgument(m_argVal2, registry);
        }
        m_argReturn = registerArgument(m_argReturn, registry);
    }

    @Override
    public void resetSimulation() {
        resetRegister(m_argReturn);
    }

    @Override
    public void simulate(Scope scope) {
        checkNextRegister(scope, m_argReturn, m_nRetValue);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
          .append(' ');
        if (isBinaryOp()) {
            sb.append(Argument.toIdString(m_typeCommon, m_nType))
              .append(", ");
        }
        sb.append(Argument.toIdString(m_argVal1, m_nValue1));
        if (hasSecondArgument()) {
            sb.append(", ")
              .append(Argument.toIdString(m_argVal2, m_nValue2));
        }
        return sb.append(", ")
                 .append(Argument.toIdString(m_argReturn, m_nRetValue))
                 .toString();
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
        // this is very similar to OpCondJump logic
        TypeConstant type1       = bctx.getArgumentType(m_nValue1);
        TypeConstant type2       = bctx.getArgumentType(m_nValue2);
        TypeConstant typeCommon  = selectCommonType(type1, type2, ErrorListener.BLACKHOLE);
        TypeConstant typeCompare = bctx.getType(m_nType); // TODO: can we get rid of it?

        if (typeCommon.equals(typeCompare)) {
            assembleUniformTest(bctx, code, null, null, typeCommon);
        } else {
            // the compiler allows a comparison of union types
            assert switch (getOpCode()) {
                case OP_IS_EQ, OP_IS_NEQ -> true;
                default -> false;
            };

            // consider the following scenario:
            //      boolean test(T1|T2 o1, T2 o2) = (o1 == o2);
            // the common type is T2, and we need to add a runtime type check for "o1" or "o2"
            RegisterInfo reg1     = null;
            RegisterInfo reg2     = null;
            Label        labelEnd = code.newLabel();
            int          nAddr    = getAddress();

            if (!type1.isA(typeCompare)) {
                reg1 = bctx.ensureRegister(code, m_nValue1);
                if (!reg1.isSingle()) {
                    throw new UnsupportedOperationException("Handle MultiSlotPrimitive");
                }
                assembleExpandedTest(bctx, code, reg1, typeCompare, labelEnd);
                reg1 = bctx.narrowTarget(code, m_nValue1, nAddr, nAddr + 1, typeCompare);
            } else {
                assert !type2.isA(typeCompare);

                reg2 = bctx.ensureRegister(code, m_nValue2);
                if (!reg2.isSingle()) {
                    throw new UnsupportedOperationException("Handle MultiSlotPrimitive");
                }
                assembleExpandedTest(bctx, code, reg2, typeCompare, labelEnd);
                reg2 = bctx.narrowTarget(code, m_nValue2, nAddr, nAddr + 1, typeCompare);
            }
        assembleUniformTest(bctx, code, reg1, reg2, typeCompare);
        code.labelBinding(labelEnd);
        }
    }

    /**
     * Assemble the test starting with a type check for the specified argument.
     */
    private void assembleExpandedTest(BuildContext bctx, CodeBuilder code,
                                      RegisterInfo regTest, TypeConstant typeCompare,
                                      Label labelEnd) {
        assert !regTest.cd().isPrimitive();

        Label labelIsA = code.newLabel();

        // o1.$xvmType(ctx).isA(typeCompare)
        code.aload(regTest.slot());
        bctx.loadCtx(code);
        code.invokevirtual(CD_nObj, "$xvmType", MD_xvmType);
        Builder.loadTypeConstant(code, bctx.typeSystem, typeCompare);
        code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA)
            .ifne(labelIsA);

        if (getOpCode() == OP_IS_EQ) {
            code.iconst_0(); // the values cannot be equal - the result is false
        } else {
            code.iconst_1(); // the values cannot be equal - the result is true
        }
        bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeBoolean()));
        code.goto_(labelEnd);
        code.labelBinding(labelIsA);
    }

    /**
     * Generate the test for arguments of the same type.
     */
    private void assembleUniformTest(BuildContext bctx, CodeBuilder code,
                                     RegisterInfo reg1, RegisterInfo reg2,
                                     TypeConstant typeCommon) {
        if (typeCommon.isPrimitive()) {
            if (reg1 == null) {
                reg1 = bctx.loadArgument(code, m_nValue1);
            } else {
                Builder.load(code, reg1.cd(), reg1.slot());
            }

            if (reg2 == null) {
                reg2 = bctx.loadArgument(code, m_nValue2);
            } else {
                Builder.load(code, reg2.cd(), reg2.slot());
            }

            ClassDesc cdCommon = JitTypeDesc.getPrimitiveClass(typeCommon);
            Label     lblTrue  = code.newLabel();
            Label     lblEnd   = code.newLabel();
            String    desc     = cdCommon.descriptorString();
            switch (desc) {
            case "I", "S", "B", "C", "Z":
                switch (getOpCode()) {
                    case OP_CMP -> {
                        code.isub();
                        generateOrdered(bctx, code);
                        return;
                    }
                    case OP_IS_EQ  -> code.if_icmpeq(lblTrue);
                    case OP_IS_NEQ -> code.if_icmpne(lblTrue);
                    case OP_IS_GT  -> code.if_icmpgt(lblTrue);
                    case OP_IS_GTE -> code.if_icmpge(lblTrue);
                    case OP_IS_LT  -> code.if_icmplt(lblTrue);
                    case OP_IS_LTE -> code.if_icmple(lblTrue);
                    default        -> throw new IllegalStateException();
                }
                break;

            case "J", "F", "D":
                switch (desc) {
                    case "J" -> code.lcmp();
                    case "F" -> code.fcmpl(); // REVIEW CP: fcmpl vs fcmpg?
                    case "D" -> code.dcmpl(); // REVIEW CP: ditto
                }
                switch (getOpCode()) {
                    case OP_CMP -> {
                        generateOrdered(bctx, code);
                        return;
                    }
                    case OP_IS_EQ  -> code.ifeq(lblTrue);
                    case OP_IS_NEQ -> code.ifne(lblTrue);
                    case OP_IS_GT  -> code.ifgt(lblTrue);
                    case OP_IS_GTE -> code.ifge(lblTrue);
                    case OP_IS_LT  -> code.iflt(lblTrue);
                    case OP_IS_LTE -> code.ifle(lblTrue);
                    default        -> throw new IllegalStateException();
                }
                break;

            default:
                throw new IllegalStateException();
            }

            code.iconst_0()
                .goto_(lblEnd)
                .labelBinding(lblTrue)
                .iconst_1()
                .labelBinding(lblEnd);
        } else {
            //  TODO: we assume typeCommon is a terminal type; we need to move some of this logic to
            //        the TypeConstant and override it for relational types
            assert typeCommon.isSingleUnderlyingClass(true);

            TypeSystem   ts   = bctx.typeSystem;
            ConstantPool pool = ts.pool();
            int          nOp  = getOpCode();
            SignatureConstant sig = switch (nOp) {
                case OP_IS_EQ, OP_IS_NEQ                      -> pool.sigEquals();
                case OP_IS_GT, OP_IS_GTE, OP_IS_LT, OP_IS_LTE -> pool.sigCompare();
                default                                       -> throw new IllegalStateException();
            };
            MethodInfo    method   = typeCommon.ensureTypeInfo().getMethodBySignature(sig);
            ClassDesc     cd       = method.getJitIdentity().getNamespace().ensureClassDesc(ts);
            JitMethodDesc jmd      = method.getJitDesc(ts, typeCommon);
            String        sJitName = method.ensureJitMethodName(ts);

            bctx.loadCtx(code);
            switch (nOp) {
            case OP_IS_EQ, OP_IS_NEQ:
                assert jmd.isOptimized;
                // Boolean equals(Ctx,xType,xObj,xObj)
                Builder.loadType(code, ts, typeCommon);
                bctx.loadArgument(code, m_nValue1);
                bctx.loadArgument(code, m_nValue2);
                code.invokestatic(cd, sJitName+OPT,
                        MethodTypeDesc.of(CD_boolean, CD_Ctx, CD_nType, cd, cd));
                if (nOp == OP_IS_NEQ) {
                    code.iconst_1()
                        .ixor();
                }
                break;

            case OP_CMP, OP_IS_GT, OP_IS_GTE, OP_IS_LT, OP_IS_LTE:
                // Ordered compare(Ctx,Type,xObj,xObj)
                Builder.loadType(code, ts, typeCommon);
                bctx.loadArgument(code, m_nValue1);
                bctx.loadArgument(code, m_nValue2);
                code.invokestatic(cd, sJitName, MethodTypeDesc.of(CD_Ordered, CD_Ctx, CD_nType, cd, cd));

                boolean fInverse;
                switch (nOp) {
                case OP_CMP:
                    bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeOrdered()));
                    return;

                case OP_IS_GT:
                    bctx.loadConstant(code, pool.valGreater());
                    fInverse = false;
                    break;
                case OP_IS_GTE:
                    bctx.loadConstant(code, pool.valLesser());
                    fInverse = true;
                    break;
                case OP_IS_LT:
                    bctx.loadConstant(code, pool.valLesser());
                    fInverse = false;
                    break;
                case OP_IS_LTE:
                    bctx.loadConstant(code, pool.valGreater());
                    fInverse = true;
                    break;
                default:
                    throw new IllegalStateException();
                }

                Label labelTrue = code.newLabel();
                if (fInverse) {
                    code.if_acmpne(labelTrue);
                } else {
                    code.if_acmpeq(labelTrue);
                }
                Label labelEnd = code.newLabel();
                code.iconst_0()
                    .goto_(labelEnd)
                    .labelBinding(labelTrue)
                    .iconst_1()
                    .labelBinding(labelEnd);
            }
        }
        bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeBoolean()));
    }

    private void generateOrdered(BuildContext bctx, CodeBuilder code) {
        throw new UnsupportedOperationException("TODO");
    }

    protected void buildUnary(BuildContext bctx, CodeBuilder code) {
        switch (getOpCode()) {
            case OP_IS_NULL, OP_IS_NNULL -> buildNullCheck(bctx, code);
            case OP_IS_TYPE, OP_IS_NTYPE -> buildTypeCheck(bctx, code);
            default                      -> throw new IllegalStateException();
        }
        bctx.storeValue(code, bctx.ensureRegInfo(m_nRetValue, bctx.pool().typeBoolean()));
    }

    private void buildNullCheck(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regArg = bctx.loadArgument(code, m_nValue1);

        Label   labelTrue = code.newLabel();
        Label   labelEnd  = code.newLabel();
        boolean fNot      = getOpCode() == OP_IS_NNULL;
        if (regArg instanceof BuildContext.DoubleSlot slotMulti) {
            assert slotMulti.flavor() == JitFlavor.MultiSlotPrimitive;

            code.iload(slotMulti.extSlot()); // True indicates Null
            if (fNot) {
                code.ifeq(labelTrue);
            } else {
                code.ifne(labelTrue);
            }
        } else {
            assert regArg.type().isNullable();

            Builder.loadNull(code);
            if (fNot) {
                code.if_acmpne(labelTrue);
            } else {
                code.if_acmpeq(labelTrue);
            }
        }

        code.iconst_0() // false
            .goto_(labelEnd)
            .labelBinding(labelTrue)
            .iconst_1() // true
            .labelBinding(labelEnd);
    }

    private void buildTypeCheck(BuildContext bctx, CodeBuilder code) {
        TypeConstant typeTarget = bctx.getArgumentType(m_nValue1);
        if (m_nValue2 <= CONSTANT_OFFSET) {
            TypeConstant typeTest = bctx.getArgumentType(m_nValue2);
            assert typeTest.isTypeOfType();
            typeTest = typeTest.getParamType(0);

            if (typeTarget.isPrimitive()) {
                // we can statically compute the result
                if (getOpCode() == OP_IS_TYPE) {
                    if (typeTarget.isA(typeTest)) {code.iconst_1();} else {code.iconst_0();}
                } else { // OP_IS_NTYPE
                    if (typeTarget.isA(typeTest)) {code.iconst_0();} else {code.iconst_1();}
                }
                return;
            } else {
                RegisterInfo regTarget = bctx.loadArgument(code, m_nValue1);
                bctx.loadCtx(code);
                code.invokevirtual(regTarget.cd(), "$xvmType", MD_xvmType); // target type
                Builder.loadTypeConstant(code, bctx.typeSystem, typeTest);   // test type
            }
        } else {
            RegisterInfo regType = bctx.loadArgument(code, m_nValue2); // xType
            assert regType.type().isTypeOfType();
            code.getfield(CD_nType, "$type", CD_TypeConstant);
        }

        code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);

        if (getOpCode() == OP_IS_NTYPE) {
            code.iconst_m1().ixor(); // "not"
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nType;
    protected int m_nValue1;
    protected int m_nValue2;
    protected int m_nRetValue;

    protected TypeConstant m_typeCommon; // the "compile time type" to use for the comparison
    protected Argument     m_argVal1;
    protected Argument     m_argVal2;
    protected Argument     m_argReturn;
}