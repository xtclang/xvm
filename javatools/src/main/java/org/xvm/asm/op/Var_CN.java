package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_CN rvalue-ref, STRING ; (next register is a named capture (a de-ref) of a Ref/Var)
 */
public class Var_CN
        extends OpVar
    {
    /**
     * Construct a VAR_CN op for the specified register, name and argument.
     *
     * @param reg        the register
     * @param constName  the name constant
     * @param argValue   the value argument of type Ref or Var
     */
    public Var_CN(Register reg, StringConstant constName, Argument argValue)
        {
        super(reg);

        if (argValue == null || constName == null)
            {
            throw new IllegalArgumentException("name and value required");
            }

        if (!argValue.getType().isA(argValue.getType().getConstantPool().typeRef()))
            {
            throw new IllegalArgumentException("value must be a Ref or Var");
            }

        m_constName = constName;
        m_argValue  = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_CN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId  = readPackedInt(in);
        m_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nNameId  = encodeArgument(m_constName, registry);
            m_nArgValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nNameId);
        writePackedLong(out, m_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_CN;
        }

    @Override
    protected boolean isTypeAware()
        {
        return false;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hRef = frame.getArgument(m_nArgValue);

            return isDeferred(hRef)
                    ? hRef.proceed(frame, frameCaller ->
                        complete(frameCaller, iPC, (RefHandle) frameCaller.popStack()))
                    :  complete(frame, iPC, (RefHandle) hRef);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, RefHandle hRef)
        {
        frame.introduceResolvedVar(m_nVar, hRef.getType(),
            frame.getString(m_nNameId), Frame.VAR_DYNAMIC_REF, hRef);
        return iPC + 1;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    public String getName(Constant[] aconst)
        {
        return getName(aconst, m_constName, m_nNameId);
        }

    private int m_nNameId;
    private int m_nArgValue;

    private StringConstant m_constName;
    private Argument       m_argValue;
    }
