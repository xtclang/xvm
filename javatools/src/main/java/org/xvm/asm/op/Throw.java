package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.javajit.Builder.CD_Exception;
import static org.xvm.javajit.Builder.CD_nException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * THROW rvalue
 */
public class Throw
        extends Op {
    /**
     * Construct a THROW op for the passed argument.
     *
     * @param argValue  the throw value Argument
     */
    public Throw(Argument argValue) {
        if (argValue == null) {
            throw new IllegalArgumentException("argument required");
        }

        m_argValue = argValue;
    }
    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Throw(DataInput in, Constant[] aconst)
            throws IOException {
        m_nArgValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argValue != null) {
            m_nArgValue = encodeArgument(m_argValue, registry);
        }

        writePackedLong(out, m_nArgValue);
    }

    @Override
    public int getOpCode() {
        return OP_THROW;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hException = frame.getArgument(m_nArgValue);
            return isDeferred(hException)
                    ? hException.proceed(frame, frameCaller ->
                        frameCaller.raiseException((ExceptionHandle) frameCaller.popStack()))
                    : frame.raiseException((ExceptionHandle) hException);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    @Override
    public boolean advances() {
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + " " + Argument.toIdString(m_argValue, m_nArgValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        // don't propagate anything
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo target = bctx.loadArgument(code, m_nArgValue);
        assert target.type().isA(bctx.pool().typeException());
        code.getfield(CD_Exception, "$exception", CD_nException)
            .athrow();
        return -1;
    }

    // ----- fields --------------------------------------------------------------------------------

    private int m_nArgValue;

    private Argument m_argValue; // never a Constant
}
