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
import org.xvm.javajit.JitMethodDesc;
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
    public void build(BuildContext bctx, CodeBuilder code) {

        TypeSystem   ts     = bctx.typeSystem;
        ConstantPool pool   = ts.pool();
        RegisterInfo regFn  = bctx.ensureRegister(code, m_nFunctionId);
        TypeConstant typeFn = regFn.type();

        assert typeFn.isFunction() && regFn.cd().equals(CD_nFunction);

        JitMethodDesc jmdBefore = JitMethodDesc.of(
                pool.extractFunctionParams(typeFn),
                pool.extractFunctionReturns(typeFn),
                false, null, Integer.MAX_VALUE, ts);

        boolean fOptBefore = jmdBefore.isOptimized;

         // initialize slots for the resulting handles
        code.aload(regFn.slot())
            .getfield(CD_nFunction, "stdMethod", CD_MethodHandle);
        int slotStd = bctx.storeTempValue(code, CD_MethodHandle);

        code.aload(regFn.slot())
            .getfield(CD_nFunction, "optMethod", CD_MethodHandle);
        int slotOpt = bctx.storeTempValue(code, CD_MethodHandle);

        code.aload(regFn.slot())
            .getfield(CD_nFunction, "immutable", CD_boolean);
        int slotImm = bctx.storeTempValue(code, CD_boolean);

        int[] anArg = m_anParamValue;
        int   cArgs = anArg.length;

        for (int i = 0; i < cArgs; i++) {
            // we assume that the indexes are sorted in the ascending order;
            // after every step, the resulting function accepts one less parameter, so it needs to
            // compensate the absolute position
            int nArgPos = m_anParamIx[i] - i;

            typeFn = pool.bindFunctionParam(typeFn, nArgPos);

            JitMethodDesc jmdAfter = JitMethodDesc.of(
                    pool.extractFunctionParams(typeFn),
                    pool.extractFunctionReturns(typeFn),
                    false, null, Integer.MAX_VALUE, ts);

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
            if (!regArg.isSingle()) {
                throw new UnsupportedOperationException("Add support for multi-slot binding");
            }

            // compensate for the Ctx argument
            int nJitPos = 1 + jmdBefore.optimizedParams[nArgPos].index;

            if (!fOptBefore) {
                assert !fOptAfter && !regArg.cd().isPrimitive();

                bindArgument(code, slotStd, nJitPos, regArg, false);
                code.aconst_null()
                    .astore(slotOpt);

                computeImmutable(code, slotImm, regArg);
            } else if (!fOptAfter) {
                assert regArg.cd().isPrimitive(); // transition from opt -> !opt

                bindArgument(code, slotStd, nJitPos, regArg, false);
                code.aconst_null()
                    .astore(slotOpt);
            } else if (regArg.cd().isPrimitive()) {
                bindArgument(code, slotStd, nJitPos, regArg, true);
                bindArgument(code, slotOpt, nJitPos, regArg, false);
            } else if (!regArg.type().isPrimitive()) {
                bindArgument(code, slotStd, nJitPos, regArg, false);
                bindArgument(code, slotOpt, nJitPos, regArg, false);

                computeImmutable(code, slotImm, regArg);
            } else {
                // the type is primitive, but the CD is not
                bindArgument(code, slotStd, nJitPos, regArg, false);
                code.aconst_null()
                    .astore(slotOpt);
            }

            fOptBefore = fOptAfter;
        }

        ClassDesc cdFn = typeFn.ensureClassDesc(ts);
        code.new_(cdFn)
            .dup()
            .aload(code.parameterSlot(0)); // ctx
        Builder.loadTypeConstant(code, ts, typeFn);
        code.aload(slotStd)
            .aload(slotOpt)
            .iload(slotImm)
            .invokespecial(cdFn, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                    CD_MethodHandle, CD_MethodHandle, CD_boolean));

        RegisterInfo regRet = bctx.ensureRegInfo(m_nRetValue, typeFn, cdFn, "");
        bctx.storeValue(code, regRet);
    }

    private static void bindArgument(CodeBuilder code, int slotMethod, int nPos,
                                     RegisterInfo regArg, boolean fBox) {
        code.aload(slotMethod)
            .ldc(nPos);

        // create an Object array with one element (for the single value to bind)
        // and store the arg value at index zero
        code.iconst_1()
            .anewarray(CD_JavaObject)
            .dup()
            .iconst_0();
        Builder.load(code, regArg);
        if (fBox) {
            Builder.box(code, regArg);
        } else if (regArg.cd().isPrimitive()) {
            Builder.boxJava(code, regArg.cd());
        }
        code.aastore();

        code.invokestatic(ClassDesc.of("java.lang.invoke.MethodHandles"), "insertArguments",
                MethodTypeDesc.of(CD_MethodHandle, CD_MethodHandle, CD_int, CD_JavaObject.arrayType()))
            .astore(slotMethod);
    }

    public static void computeImmutable(CodeBuilder code, int slotImm, RegisterInfo regArg) {
        Label labelEnd = code.newLabel();
        code.iload(slotImm)
            .ifeq(labelEnd);
        Builder.load(code, regArg);
        code.invokevirtual(CD_nObj, "$isImmut", MethodTypeDesc.of(CD_boolean))
            .iand()
            .istore(slotImm)
            .labelBinding(labelEnd);
    }

    // ----- fields --------------------------------------------------------------------------------

    private final int[] m_anParamIx;
    private       int[] m_anParamValue;

    private Argument[] m_aArgParam;
}