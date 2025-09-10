package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * CALL_N0 rvalue-function, #params:(rvalue)
 */
public class Call_N0
        extends OpCallable {
    /**
     * Construct a CALL_N0 op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue     the array of value Arguments
     */
    public Call_N0(Argument argFunction, Argument[] aArgValue) {
        super(argFunction);

        m_aArgValue = aArgValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_N0(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgValue != null) {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
        }

        writeIntArray(out, m_anArgValue);
    }

    @Override
    public int getOpCode() {
        return OP_CALL_N0;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            if (m_nFunctionId == A_SUPER) {
                CallChain chain = frame.m_chain;
                if (chain == null) {
                    throw new IllegalStateException();
                }

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, chain.getSuper(frame).getMaxVars());

                if (anyDeferred(ahVar)) {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperN1(frame, ahVar, A_IGNORE, false);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

                return chain.callSuperN1(frame, ahVar, A_IGNORE, false);
            }

            if (m_nFunctionId <= CONSTANT_OFFSET) {
                MethodStructure function = getMethodStructure(frame);
                if (function == null) {
                    return R_EXCEPTION;
                }

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, function.getMaxVars());

                if (anyDeferred(ahVar)) {
                    Frame.Continuation stepNext = frameCaller ->
                        frame.call1(function, null, ahVar, A_IGNORE);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

                if (function.isNative()) {
                    return getNativeTemplate(frame, function).
                        invokeNativeN(frame, function, null, ahVar, A_IGNORE);
                }

                return frame.call1(function, null, ahVar, A_IGNORE);
            }

            ObjectHandle hFunction = frame.getArgument(m_nFunctionId);

            return isDeferred(hFunction)
                    ? hFunction.proceed(frame, frameCaller ->
                        complete(frameCaller, (FunctionHandle) frameCaller.popStack()))
                    : complete(frame, (FunctionHandle) hFunction);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, FunctionHandle hFunction) {
        try {
            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, hFunction.getVarCount());
            if (anyDeferred(ahVar)) {
                Frame.Continuation stepNext = frameCaller ->
                    hFunction.call1(frameCaller, null, ahVar, A_IGNORE);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
            }

            return hFunction.call1(frame, null, ahVar, A_IGNORE);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
    }

    @Override
    protected String getParamsString() {
        return getParamsString(m_anArgValue, m_aArgValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        buildCall(bctx, code, m_anArgValue);
    }

    // ----- fields --------------------------------------------------------------------------------

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
}