package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWG_1 CONSTRUCT, TYPE, rvalue-param, lvalue
 */
public class NewG_1
        extends OpCallable
    {
    /**
     * Construct a NEWG_1 op.
     *
     * @param nConstructorId  identifies the constructor
     * @param nType           the type of the object being created
     * @param nArg            the constructor argument
     * @param nRet            the location to store the new object
     *
     * @deprecated
     */
    public NewG_1(int nConstructorId, int nType, int nArg, int nRet)
        {
        super(null);

        m_nFunctionId = nConstructorId;
        m_nTypeValue = nType;
        m_nArgValue  = nArg;
        m_nRetValue  = nRet;
        }

    /**
     * Construct a NEW_1 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argType      the type Argument
     * @param argValue     the array of value Arguments
     * @param argReturn    the return Argument
     */
    public NewG_1(MethodConstant constMethod, Argument argType, Argument argValue, Argument argReturn)
        {
        super(constMethod);

        m_argType = argType;
        m_argValue = argValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewG_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nTypeValue = readPackedInt(in);
        m_nArgValue  = readPackedInt(in);
        m_nRetValue  = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argType != null)
            {
            m_nTypeValue = encodeArgument(m_argType, registry);
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nTypeValue);
        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWG_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            MethodStructure constructor = getMethodStructure(frame);

            ObjectHandle[] ahVar = frame.getArguments(
                new int[]{m_nArgValue}, constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            TypeComposition clzTarget = frame.resolveClass(m_nTypeValue);
            ClassTemplate template = clzTarget.getTemplate();

            assert constructor.getParent().getParent() == template.f_struct;

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceResolvedVar(clzTarget.ensurePublicType());
                }

            if (isProperty(ahVar[0]))
                {
                Frame.Continuation stepNext = frameCaller ->
                    template.construct(frame, constructor, clzTarget, ahVar, m_nRetValue);

                return new Utils.GetArgument(ahVar, stepNext).doNext(frame);
                }

            return template.construct(frame, constructor, clzTarget, ahVar, m_nRetValue);
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

        registerArgument(m_argType, registry);
        registerArgument(m_argValue, registry);
        }

    private int m_nTypeValue;
    private int m_nArgValue;

    private Argument m_argType;
    private Argument m_argValue;
    }
