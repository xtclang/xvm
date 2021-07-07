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
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEW_1 CONSTRUCT, rvalue-param, lvalue-return
 */
public class New_1
        extends OpCallable
    {
    /**
     * Construct a NEW_1 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argValue     the value Argument
     * @param argReturn    the return Argument
     */
    public New_1(MethodConstant constMethod, Argument argValue, Argument argReturn)
        {
        super(constMethod);

        m_argValue = argValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public New_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEW_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame);
        if (constructor == null)
            {
            return R_EXCEPTION;
            }

        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArgValue);

            return isDeferred(hArg)
                    ? hArg.proceed(frame, frameCaller ->
                        complete(frameCaller, constructor, frameCaller.popStack()))
                    : complete(frame, constructor, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int complete(Frame frame, MethodStructure constructor, ObjectHandle hArg)
        {
        IdentityConstant constClz  = constructor.getParent().getParent().getIdentityConstant();
        ClassTemplate    template  = frame.ensureTemplate(constClz);
        ClassComposition clzTarget = template.getCanonicalClass();
        ObjectHandle     hParent   = clzTarget.isInstanceChild() ? frame.getThis() : null;

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
            }

        ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];
        ahVar[0] = hArg;

        return template.construct(frame, constructor, clzTarget, hParent, ahVar, m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argValue, m_nArgValue);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }
