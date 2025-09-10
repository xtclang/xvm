package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * L_SET PROPERTY, rvalue ; set local property
 */
public class L_Set
        extends OpProperty {
    /**
     * Construct an L_SET op based on the specified arguments.
     *
     * @param idProp    the property id
     * @param argValue  the value Argument
     */
    public L_Set(PropertyConstant idProp, Argument argValue) {
        super(idProp);

        m_argValue = argValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public L_Set(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argValue != null) {
            m_nValue = encodeArgument(m_argValue, registry);
        }

        writePackedLong(out, m_nValue);
    }

    @Override
    public int getOpCode() {
        return OP_L_SET;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hValue  = frame.getArgument(m_nValue);
            ObjectHandle hTarget = frame.getThis();

            PropertyConstant idProp = (PropertyConstant) frame.getConstant(m_nPropId);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        hTarget.getTemplate().
                            setPropertyValue(frameCaller, hTarget, idProp, frameCaller.popStack()))
                    : hTarget.getTemplate().
                        setPropertyValue(frame, hTarget, idProp, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
    }

    @Override
    public String toString() {
        return super.toString() + ", " + Argument.toIdString(m_argValue, m_nValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        bctx.buildSetProperty(code, bctx.loadThis(code), m_nPropId, m_nValue);
    }

    // ----- fields --------------------------------------------------------------------------------

    private int m_nValue;

    private Argument m_argValue;
}
