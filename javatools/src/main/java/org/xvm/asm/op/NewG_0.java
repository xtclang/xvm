package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWG_0 CONSTRUCT, TYPE, lvalue ; generic-type "new"
 */
public class NewG_0
        extends OpCallable {
    /**
     * Construct a NEWG_0 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argType      the type Argument
     * @param argReturn    the return Argument
     */
    public NewG_0(MethodConstant constMethod, Argument argType, Argument argReturn) {
        super(constMethod);

        m_argType = argType;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewG_0(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nTypeValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argType != null) {
            m_nTypeValue = encodeArgument(m_argType, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        writePackedLong(out, m_nTypeValue);
        writePackedLong(out, m_nRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_NEWG_0;
    }

    @Override
    public int process(Frame frame, int iPC) {
        MethodStructure constructor = getMethodStructure(frame);
        if (constructor == null) {
            return R_EXCEPTION;
        }

        TypeComposition clzTarget = frame.resolveClass(m_nTypeValue);
        ObjectHandle    hParent  = clzTarget.isInstanceChild() ? frame.getThis() : null;

        if (frame.isNextRegister(m_nRetValue)) {
            frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
        }

        ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];

        return clzTarget.getTemplate().
                construct(frame, constructor, clzTarget, hParent, ahVar, m_nRetValue);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_argType = registerArgument(m_argType, registry);
    }

    private int m_nTypeValue;

    private Argument m_argType;
}