package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.NumberSupport;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeMatrix;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * GP_DIVREM rvalue1, rvalue2, lvalue1-quotient, lvalue2-remainder ; T /% T -> T, T
 */
public class GP_DivRem
        extends Op
        implements NumberSupport {
    /**
     * Construct a GP_DIVREM op for the passed arguments.
     *
     * @param argTarget   the target Argument
     * @param argValue    the second value Argument
     * @param aargReturn  the two Arguments to store the results into
     */
    public GP_DivRem(Argument argTarget, Argument argValue, Argument[] aargReturn) {
        m_argTarget  = argTarget;
        m_argValue   = argValue;
        m_aargReturn = aargReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_DivRem(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTarget    = readPackedInt(in);
        m_nArgValue  = readPackedInt(in);
        m_anRetValue = readIntArray(in);
    }

    @Override
    public int getOpCode() {
        return OP_GP_DIVREM;
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTarget != null) {
            m_nTarget    = encodeArgument(m_argTarget, registry);
            m_nArgValue  = encodeArgument(m_argValue,  registry);
            m_anRetValue = encodeArguments(m_aargReturn, registry);
        }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nArgValue);
        writeIntArray(out, m_anRetValue);
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nArgValue}, 2);

            if (frame.isNextRegister(m_anRetValue[0])) {
                frame.introduceVarCopy(m_anRetValue[0], m_nTarget); // TODO GG review this (type comes from op method)
            }

            if (frame.isNextRegister(m_anRetValue[1])) {
                frame.introduceVarCopy(m_anRetValue[1], m_nTarget);
            }

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

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return hTarget.getOpSupport().invokeDivRem(frame, hTarget, hArg, m_anRetValue);
    }

    @Override
    public void resetSimulation() {
        resetRegisters(m_aargReturn);
    }

    @Override
    public void simulate(Scope scope) {
        checkNextRegisters(scope, m_aargReturn, m_anRetValue);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_argValue  = registerArgument(m_argValue, registry);
        registerArguments(m_aargReturn, registry);
    }

    @Override
    public String toString() {
        Argument argRet0 = m_aargReturn == null ? null : m_aargReturn[0];
        Argument argRet1 = m_aargReturn == null ? null : m_aargReturn[1];
        int      nRet0   = m_anRetValue == null ? 0    : m_anRetValue[0];
        int      nRet1   = m_anRetValue == null ? 0    : m_anRetValue[1];

        return super.toString()
                + ' '  + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argValue , m_nArgValue)
                + ", " + Argument.toIdString(argRet0    , nRet0)
                + ", " + Argument.toIdString(argRet1    , nRet1);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        TypeMatrix tmx = bctx.typeMatrix;

        TypeConstant   typeTarget = bctx.getArgumentType(m_nTarget);
        MethodInfo     method     = findOpMethod(bctx, typeTarget);
        TypeConstant[] rawReturns = method.getSignature().getRawReturns();

        for (int i = 0; i < rawReturns.length; i++) {
            TypeConstant typeResult = rawReturns[0];
            if (!typeResult.equals(typeTarget)) {
                tmx.assign(getAddress(), m_anRetValue[i],
                        typeResult.resolveAutoNarrowing(bctx.pool(), false, typeTarget, null));
                return;
            }
        }

        // for all other ops/scenarios the types do not change
        tmx.follow(getAddress());
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regTarget  = bctx.ensureRegister(code, m_nTarget);
        ClassDesc    cdTarget   = regTarget.cd();
        TypeConstant typeTarget = regTarget.type();

        if (cdTarget.isPrimitive()) {
            if (!regTarget.isSingle()) {
                throw new UnsupportedOperationException(toName(getOpCode())
                        + " operation on multi-slot");
            }
            // perform a div, resulting quotient will be on the stack
            regTarget.load(code);
            bctx.loadArgument(code, m_nArgValue);
            buildPrimitiveDiv(bctx, code, regTarget);
            // store quotient
            bctx.storeValue(code, m_anRetValue[0], typeTarget);
            // calculate the remainder (leaving the result on the stack)
            buildPrimitiveRemainder(bctx, code, regTarget, m_nArgValue, m_anRetValue[0]);
            // store the remainder
            bctx.storeValue(code, m_anRetValue[1], typeTarget);
        } else if (typeTarget.isXvmPrimitive()) {
            // perform a div, quotient will be on the stack
            buildXvmPrimitiveDiv(bctx, code, regTarget, m_nArgValue);
            // store quotient
            bctx.storeValue(code, m_anRetValue[0], typeTarget);
            // calculate the remainder (leaving the result on the stack)
            buildXvmPrimitiveRemainder(bctx, code, regTarget, m_nArgValue, m_anRetValue[0]);
            // store the remainder
            bctx.storeValue(code, m_anRetValue[1], typeTarget);
        } else {
            MethodInfo    method   = findOpMethod(bctx, typeTarget);
            String        sJitName = method.ensureJitMethodName(bctx.typeSystem);
            JitMethodDesc jmd      = method.getJitDesc(bctx.builder, typeTarget);

            MethodTypeDesc md;
            if (jmd.isOptimized) {
                md        = jmd.optimizedMD;
                sJitName += Builder.OPT;
            } else {
                md = jmd.standardMD;
            }

            regTarget.load(code);
            if (jmd.isOptimizedStatic) {
                // the target must be a boxed primitive
                assert typeTarget.isJitPrimitive();
                Builder.unbox(code, typeTarget);
            }
            bctx.loadCtx(code);
            bctx.loadCallArguments(code, jmd, new int[] {m_nArgValue});
            if (jmd.isOptimizedStatic) {
                code.invokestatic(bctx.builder.ensureClassDesc(typeTarget), sJitName, md);
            } else {
                code.invokevirtual(regTarget.cd(), sJitName, md);
            }

            TypeConstant typeReturn = method.getSignature().getRawReturns()[0]; // could differ from target
            TypeConstant typeResult = typeReturn.resolveAutoNarrowing(bctx.pool(), false, typeTarget, null);

            // the quotient is on the stack
            if (!typeReturn.isA(typeResult)) {
                code.checkcast(bctx.builder.ensureClassDesc(typeResult));
            }
            bctx.storeValue(code, m_anRetValue[0], typeTarget);
            // the remainder is in the context
            bctx.loadFromContext(code, cdTarget, 0);
            if (!typeReturn.isA(typeResult)) {
                code.checkcast(bctx.builder.ensureClassDesc(typeResult));
            }
            bctx.storeValue(code, m_anRetValue[1], typeTarget);
        }
        return -1;
    }

    /**
     * Find the op method.
     */
    private MethodInfo findOpMethod(BuildContext bctx, TypeConstant typeTarget) {
        return bctx.getTypeInfo(typeTarget)
                .findOpMethod("divrem", "/%", bctx.getArgumentType(m_nArgValue));
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int   m_nTarget;
    protected int   m_nArgValue;
    protected int[] m_anRetValue;

    private Argument   m_argTarget;
    private Argument   m_argValue;
    private Argument[] m_aargReturn;
}
