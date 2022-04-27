package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEW_0 CONSTRUCT, lvalue-return
 */
public class New_0
        extends OpCallable
    {
    /**
     * Construct a NEW_0 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argReturn    the return Argument
     */
    public New_0(MethodConstant constMethod, Argument argReturn)
        {
        super(constMethod);

        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public New_0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argReturn != null)
            {
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEW_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame);
        if (constructor == null)
            {
            return R_EXCEPTION;
            }

        ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];

        IdentityConstant constClz  = constructor.getParent().getParent().getIdentityConstant();
        ClassTemplate    template  = frame.ensureTemplate(constClz);
        ClassComposition clzTarget = template.getCanonicalClass(frame.poolContext());
        ObjectHandle     hParent   = clzTarget.isInstanceChild() ? frame.getThis() : null;

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
            }

        return template.construct(frame, constructor, clzTarget, hParent, ahVar, m_nRetValue);
        }
    }