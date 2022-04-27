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
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEW_N CONSTRUCT, #params:(rvalue), lvalue-return
 */
public class New_N
        extends OpCallable
    {
    /**
     * Construct a NEW_1 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param aArgValue    the array of value Arguments
     * @param argReturn    the return Argument
     */
    public New_N(MethodConstant constMethod, Argument[] aArgValue, Argument argReturn)
        {
        super(constMethod);

        checkElementsNonNull(aArgValue);

        m_aArgValue = aArgValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public New_N(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEW_N;
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
            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, constructor.getMaxVars());

            IdentityConstant constClz  = constructor.getParent().getParent().getIdentityConstant();
            ClassTemplate    template  = frame.ensureTemplate(constClz);
            ClassComposition clzTarget = template.getCanonicalClass(frame.poolContext());
            ObjectHandle     hParent   = clzTarget.isInstanceChild() ? frame.getThis() : null;

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
                }

            if (anyDeferred(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    template.construct(frame, constructor, clzTarget, hParent, ahVar, m_nRetValue);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }
            return template.construct(frame, constructor, clzTarget, hParent, ahVar, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return getParamsString(m_anArgValue, m_aArgValue);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }