package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

/**
 * NVOK_0N rvalue-target, CONST-METHOD, #returns:(lvalue)
 */
public class Invoke_0N
        extends OpInvocable {
    /**
     * Construct an NVOK_0N op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aArgReturn   the array of Registers to move the results into
     */
    public Invoke_0N(Argument argTarget, MethodConstant constMethod, Argument[] aArgReturn) {
        super(argTarget, constMethod);

        m_aArgReturn = aArgReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_0N(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_anRetValue = readIntArray(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgReturn != null) {
            m_anRetValue = encodeArguments(m_aArgReturn, registry);
        }

        writeIntArray(out, m_anRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_NVOK_0N;
    }

    @Override
    protected boolean isMultiReturn() {
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                        complete(frameCaller, frameCaller.popStack()))
                    : complete(frame, hTarget);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, ObjectHandle hTarget) {
        checkReturnRegisters(frame, hTarget);

        return getCallChain(frame, hTarget).invoke(frame, hTarget, Utils.OBJECTS_NONE, m_anRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        buildInvoke(bctx, code, NO_ARGS);
    }
}
