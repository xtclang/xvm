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
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWG_T CONSTRUCT, TYPE, rvalue-tparams, lvalue
 */
public class NewG_T
        extends OpCallable
    {
    /**
     * Construct a NEWG_T op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argType      the type Argument
     * @param argValue     the array of value Arguments
     * @param argReturn    the return Argument
     */
    public NewG_T(MethodConstant constMethod, Argument argType, Argument argValue, Argument argReturn)
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
    public NewG_T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nTypeValue = readPackedInt(in);
        m_nArgTupleValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argType != null)
            {
            m_nTypeValue = encodeArgument(m_argType, registry);
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nTypeValue);
        writePackedLong(out, m_nArgTupleValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWG_T;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArgTupleValue);

            return isDeferred(hArg)
                    ? hArg.proceed(frame, frameCaller ->
                        complete(frameCaller, ((TupleHandle) frameCaller.popStack()).m_ahValue))
                    : complete(frame, ((TupleHandle) hArg).m_ahValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle[] ahArg)
        {
        MethodStructure constructor = getMethodStructure(frame);
        if (constructor == null)
            {
            return R_EXCEPTION;
            }

        TypeComposition clzTarget = frame.resolveClass(m_nTypeValue);
        ObjectHandle    hParent   = clzTarget.isInstanceChild() ? frame.getThis() : null;

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nRetValue, clzTarget.getType());
            }

        return clzTarget.getTemplate().construct(frame, constructor, clzTarget, hParent,
            Utils.ensureSize(ahArg, constructor.getMaxVars()), m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argType = registerArgument(m_argType, registry);
        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argType, m_nTypeValue) + ": " +
               Argument.toIdString(m_argValue, m_nArgTupleValue);
        }

    private int m_nTypeValue;
    private int m_nArgTupleValue;

    private Argument m_argType;
    private Argument m_argValue;
    }