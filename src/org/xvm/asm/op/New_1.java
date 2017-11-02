package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.Register;
import org.xvm.asm.Scope;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEW_1 CONST-CONSTRUCT, rvalue-param, lvalue-return
 */
public class New_1
        extends OpCallable
    {
    /**
     * Construct a NEW_1 op.
     *
     * @param nConstructorId  identifies the constructor
     * @param nArg            the constructor argument
     * @param nRet            the location to store the new object
     *
     * @deprecated
     */
    public New_1(int nConstructorId, int nArg, int nRet)
        {
        super(null);

        m_nFunctionId = nConstructorId;
        m_nArgValue = nArg;
        m_nRetValue = nRet;
        }

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

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(
                    new int[]{m_nArgValue}, constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            IdentityConstant constClass = constructor.getParent().getParent().getIdentityConstant();
            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVar(constClass.asTypeConstant().getPosition());
                }

            if (isProperty(ahVar[0]))
                {
                Frame.Continuation stepNext = frameCaller -> template.construct(frame, constructor,
                        template.f_clazzCanonical, ahVar, m_nRetValue);

                return new Utils.GetArgument(ahVar, stepNext).doNext(frame);
                }

            return template.construct(frame, constructor,
                    template.f_clazzCanonical, ahVar, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isNextRegister(m_argReturn))
            {
            ((Register) m_argReturn).assignIndex(scope.allocVar());
            }
        // TODO: remove when deprecated construction is removed
        else
        if (scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        registerArgument(m_argReturn, registry);
        }

    private int m_nArgValue;
    private int m_nRetValue;

    private Argument m_argValue;
    private Argument m_argReturn;
    }
