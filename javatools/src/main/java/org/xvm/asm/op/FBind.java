package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeSystem;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaObject;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nFunction;
import static org.xvm.javajit.Builder.CD_nObj;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * FBIND rvalue-fn, #params:(param-index, rvalue-param), lvalue-fn-result
 */
public class FBind
        extends OpCallable {
    /**
     * Construct an FBIND op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param anParamIx    the indexes of parameter(s) to bind (sorted in ascending order)
     * @param aArgValue    the array of Arguments to bind the values to
     * @param argReturn    the return Argument
     */
    public FBind(Argument argFunction, int[] anParamIx, Argument[] aArgValue, Argument argReturn) {
        super(argFunction);

        m_anParamIx = anParamIx;
        m_aArgParam = aArgValue;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FBind(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        int c = readPackedInt(in);

        m_anParamIx    = new int[c];
        m_anParamValue = new int[c];

        for (int i = 0; i < c; i++) {
            m_anParamIx[i]    = readPackedInt(in);
            m_anParamValue[i] = readPackedInt(in);
        }
        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgParam != null) {
            m_anParamValue = encodeArguments(m_aArgParam, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        int c = m_anParamIx.length;
        writePackedLong(out, c);
        for (int i = 0; i < c; i++) {
            writePackedLong(out, m_anParamIx[i]);
            writePackedLong(out, m_anParamValue[i]);
        }
        writePackedLong(out, m_nRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_FBIND;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            int            nFunctionId = m_nFunctionId;
            FunctionHandle hFunction;

            if (nFunctionId == A_SUPER) {
                CallChain chain = frame.m_chain;
                if (chain == null) {
                    throw new IllegalStateException();
                }
                int nDepth = frame.m_nChainDepth + 1;
                if (nDepth >= chain.getDepth()) {
                    return frame.raiseException("Invalid \"super\" reference");
                }
                hFunction = xRTFunction.makeHandle(frame, chain, nDepth).bindTarget(frame, frame.getThis());
            } else if (nFunctionId <= CONSTANT_OFFSET) {
                MethodStructure function = getMethodStructure(frame);
                if (function == null) {
                    return R_EXCEPTION;
                }

                ObjectHandle hFn = xRTFunction.makeHandle(frame, function);

                if (isDeferred(hFn)) {
                    return hFn.proceed(frame, frameCaller ->
                        resolveArguments(frameCaller, (FunctionHandle) frameCaller.popStack()));
                }
                hFunction = (FunctionHandle) hFn;
            } else {
                ObjectHandle hFn = frame.getArgument(nFunctionId);

                if (isDeferred(hFn)) {
                    return hFn.proceed(frame, frameCaller ->
                        resolveArguments(frameCaller, (FunctionHandle) frameCaller.popStack()));
                }
                hFunction = (FunctionHandle) hFn;
            }
            return resolveArguments(frame, hFunction);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int resolveArguments(Frame frame, FunctionHandle hFunction) {
        try {
            ObjectHandle[] ahParam = frame.getArguments(m_anParamValue, 0);

            if (anyDeferred(ahParam)) {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, hFunction, ahParam);

                return new Utils.GetArguments(ahParam, stepNext).doNext(frame);
            }

            return complete(frame, hFunction, ahParam);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, FunctionHandle hFunction, ObjectHandle[] ahParam) {
        // TODO: introduce bindMulti() method to reduce array copying
        // we assume that the indexes are sorted in the ascending order
        int[] anParamIx = m_anParamIx;
        for (int i = 0, c = anParamIx.length; i < c; i++) {
            // after every step, the resulting function accepts one less
            // parameter, so it needs to compensate the absolute position
            hFunction = hFunction.bind(frame, anParamIx[i] - i, ahParam[i]);
        }

        int nRetVal = m_nRetValue;
        if (frame.isNextRegister(nRetVal)) {
            // do we need a precise type?
            frame.introduceResolvedVar(nRetVal, frame.poolContext().typeFunction());
        }

        return frame.assignValue(nRetVal, hFunction);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        registerArguments(m_aArgParam, registry);
    }

    @Override
    protected String getParamsString() {
        StringBuilder sb = new StringBuilder();
        int cArgNums = m_anParamValue == null ? 0 : m_anParamValue.length;
        int cArgRefs = m_aArgParam    == null ? 0 : m_aArgParam   .length;
        for (int i = 0, c = m_anParamIx.length; i < c; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('[')
              .append(m_anParamIx[i])
              .append("]=")
              .append(Argument.toIdString(i < cArgRefs ? m_aArgParam[i] : null,
                      i < cArgNums ? m_anParamValue[i] : Register.UNKNOWN));
        }
        return sb.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        TypeConstant typeFn = bctx.getArgumentType(m_nFunctionId);

        assert typeFn.isFunction();

        for (int i = 0, c = m_anParamIx.length; i < c; i++) {
            // we assume that the indexes are sorted in the ascending order;
            // after every step, the resulting function accepts one less parameter, so it needs to
            // compensate the absolute position
            typeFn = bctx.pool().bindFunctionParam(typeFn, m_anParamIx[i] - i);
        }

        bctx.typeMatrix.assign(getAddress(), m_nRetValue, typeFn);
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        TypeSystem   ts     = bctx.typeSystem;
        ConstantPool pool   = ts.pool();
        RegisterInfo regFn  = bctx.ensureRegister(code, m_nFunctionId);
        TypeConstant typeFn = regFn.type();

        assert typeFn.isFunction() && regFn.cd().equals(CD_nFunction);

        JitMethodDesc jmdBefore = JitMethodDesc.of(bctx.builder, typeFn, true, false,
                pool.extractFunctionParams(typeFn), pool.extractFunctionReturns(typeFn),
                Integer.MAX_VALUE);

        boolean fOptBefore = jmdBefore.isOptimized;

         // initialize slots for the resulting handles
        regFn.load(code);
        code.getfield(CD_nFunction, "stdMethod", CD_MethodHandle);
        int slotStd = bctx.storeTempValue(code, CD_MethodHandle);

        regFn.load(code);
        code.getfield(CD_nFunction, "optMethod", CD_MethodHandle);
        int slotOpt = bctx.storeTempValue(code, CD_MethodHandle);

        regFn.load(code);
        code.getfield(CD_nFunction, "immutable", CD_boolean);
        int slotImm = bctx.storeTempValue(code, CD_boolean);

        int[] anArg = m_anParamValue;
        int   cArgs = anArg.length;

        for (int i = 0; i < cArgs; i++) {
            // we assume that the indexes are sorted in the ascending order;
            // after every step, the resulting function accepts one less parameter, so it needs to
            // compensate the absolute position
            int nArgPos = m_anParamIx[i] - i;

            typeFn = pool.bindFunctionParam(typeFn, nArgPos);

            JitMethodDesc jmdAfter = JitMethodDesc.of(bctx.builder,
                    null, true, false, pool.extractFunctionParams(typeFn),
                    pool.extractFunctionReturns(typeFn),
                    Integer.MAX_VALUE);

            boolean fOptAfter = jmdAfter.isOptimized;

            /* The code we need to generate looks like the following:
                 MethodHandle stdMethod = method.stdMethod;
                 MethodHandle optMethod = method.optMethod;
                 boolean      imm       = method.imm;
                 for (arg : args) {
                     if (!fOptBefore) {
                        stdMethod = MethodHandles.insertArguments(stdMethod, i, arg);
                        optMethod = null;
                        imm      &= arg.$isImmut();
                     } else if (!OptAfter) {
                        stdMethod = MethodHandles.insertArguments(optMethod, i, arg);
                        optMethod = null;
                     } else {
                        if (arg is unboxed) {
                            stdMethod = MethodHandles.insertArguments(stdMethod, i, box(arg));
                            optMethod = MethodHandles.insertArguments(optMethod, i, arg);
                        } else if (arg is not primitive) {
                            stdMethod = MethodHandles.insertArguments(stdMethod, i, arg);
                            optMethod = MethodHandles.insertArguments(optMethod, i, arg);
                            imm      &= arg.$isImmut();
                        } else { // primitive, but not unboxed - conditional boxing failed
                            stdMethod = MethodHandles.insertArguments(stdMethod, i, arg);
                            optMethod = null;
                        }
                     }
                 }
                 retValue = new FunctionN(ctx, std, opt, imm);
            */

            RegisterInfo regArg = bctx.ensureRegister(code, anArg[i]);

            // the standard position only needs to be offset by the implicits (ctx)
            int          cImplicits = jmdBefore.getImplicitParamCount();
            JitParamDesc pdArg      = jmdBefore.standardParams[nArgPos];
            int          nStdPos    = cImplicits + pdArg.index;

            TypeConstant argType = regArg.type();
            if (!fOptBefore) {
                assert !fOptAfter;
                bindArgument(code, slotStd, nStdPos, regArg, JitFlavor.Specific);
                code.aconst_null()
                    .astore(slotOpt);

                // all JIT primitives are immutable, so we can only become mutable after binding to
                // a non-primitve mutable value
                if (!argType.isJitPrimitive()) {
                    computeImmutable(code, slotImm, regArg);
                }
            } else if (!fOptAfter) {
                assert argType.isJitPrimitive(); // transition from opt -> !opt

                bindArgument(code, slotStd, nStdPos, regArg, JitFlavor.Specific);
                code.aconst_null()
                    .astore(slotOpt);
            } else {
                // even though the "jmdBefore.isOptimized" may be true, the actual parameter we are
                // binding here may not be optimized (e.g. Int64 -> Number), in which case we will
                // need to box any primitive values when binding
                int       nOptIndex = jmdBefore.getOptimizedParamIndex(nArgPos);
                JitFlavor flavor    = jmdBefore.optimizedParams[nOptIndex].flavor;
                int       nOptPos   = cImplicits + nOptIndex;

                bindArgument(code, slotStd, nStdPos, regArg, JitFlavor.Specific);
                bindArgument(code, slotOpt, nOptPos, regArg, flavor);

                if (!argType.isJitPrimitive()) {
                    // it can become mutable
                    computeImmutable(code, slotImm, regArg);
                }
            }

            fOptBefore = fOptAfter;
        }

        ClassDesc cdFn = bctx.builder.ensureClassDesc(typeFn);
        code.new_(cdFn)
            .dup()
            .aload(code.parameterSlot(0)); // ctx
        bctx.loadTypeConstant(code, typeFn);
        code.aload(slotStd)
            .aload(slotOpt)
            .iload(slotImm)
            .invokespecial(cdFn, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                    CD_MethodHandle, CD_MethodHandle, CD_boolean));

        RegisterInfo regRet = bctx.ensureRegister(m_nRetValue, typeFn, cdFn, "");
        bctx.storeValue(code, regRet, typeFn);
        return -1;
    }

    /**
     * Bind the argument.
     *
     * @param slotMethod  the slot for the MethodHandle
     * @param nPos        the position of the first argument to bind
     * @param regArg      the register representing the value to bind
     * @param flavor      the flavor of the destination argument
     */
    private static void bindArgument(CodeBuilder code, int slotMethod, int nPos,
                                     RegisterInfo regArg, JitFlavor flavor) {
        code.aload(slotMethod)
            .ldc(nPos);

        if (regArg.type().isJitPrimitive()) {
            if (flavor.isOptimized) {
                int[] slots = regArg.slots();
                int   cVals = slots.length;
                int   cBind = slots.length;

                if (regArg.flavor() != flavor) {
                    // this can only mean that the destination flavor is "Nullable" while the source
                    // register is not
                    assert !regArg.type().isNullable() && flavor.isNullablePrimitive();
                    cBind++;
                }

                code.loadConstant(cBind)
                    .anewarray(CD_JavaObject);

                // duplicate the array reference for each additional element
                for (int i = 0; i < cBind; i++) {
                    code.dup();
                }

                ClassDesc[] cds = regArg.slotCds();
                for (int i = 0; i < cVals; i++) {
                    code.loadConstant(i);
                    Builder.load(code, cds[i], slots[i]);
                    Builder.boxJava(code, cds[i]);
                    code.aastore();
                }

                if (cVals < cBind) {
                    code.loadConstant(cVals);
                    code.iconst_0(); // false indicates "not Null"
                    Builder.boxJava(code, CD_boolean);
                    code.aastore();
                }
            } else {
                code.loadConstant(1)
                    .anewarray(CD_JavaObject)
                    .dup()
                    .iconst_0();
                Builder.box(code, regArg.load(code));
                code.aastore();
            }
        } else {
            code.loadConstant(1)
                .anewarray(CD_JavaObject)
                .dup()
                .iconst_0();
            regArg.load(code);
            code.aastore();
        }

        code.invokestatic(ClassDesc.of("java.lang.invoke.MethodHandles"), "insertArguments",
                MethodTypeDesc.of(CD_MethodHandle, CD_MethodHandle, CD_int, CD_JavaObject.arrayType()))
            .astore(slotMethod);
    }

    /**
     * Check if the specified (newly bound) argument is mutable and change the immutability flag
     * for the MethodHandle accordingly.
     */
    private static void computeImmutable(CodeBuilder code, int slotImm, RegisterInfo regArg) {
        // this.immutable &= ((nObj) value).$isImmut();
        Label labelEnd = code.newLabel();
        code.iload(slotImm)
            .ifeq(labelEnd);
        regArg.load(code);
        if (regArg.type().isJitInterface()) {
            code.checkcast(CD_nObj);
        }
        code.invokevirtual(CD_nObj, "$isImmut", MethodTypeDesc.of(CD_boolean))
            .istore(slotImm)
            .labelBinding(labelEnd);
    }

    // ----- fields --------------------------------------------------------------------------------

    private final int[] m_anParamIx;
    private       int[] m_anParamValue;

    private Argument[] m_aArgParam;
}