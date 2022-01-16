package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWV_N CONSTRUCT, rvalue-type, #:(rvalue), lvalue; virtual "new"
 */
public class NewV_N
        extends OpCallable
    {
    /**
     * Construct a NEWV_N op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argType      the type Argument
     * @param aargValue    the values Arguments
     * @param argReturn    the return Argument
     */
    public NewV_N(MethodConstant constMethod, Argument argType, Argument[] aargValue,
                  Argument argReturn)
        {
        super(constMethod);

        m_argType   = argType;
        m_aArgValue = aargValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewV_N(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nType      = readPackedInt(in);
        m_anArgValue = readIntArray(in);
        m_nRetValue  = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argType != null)
            {
            m_nType      = encodeArgument(m_argType, registry);
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_nRetValue  = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nType);
        writeIntArray(out,   m_anArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWV_N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle   hType = frame.getArgument(m_nType);
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, 0);

            return isDeferred(hType)
                    ? hType.proceed(frame, frameCaller ->
                        collectArgs(frameCaller, (TypeHandle) frameCaller.popStack(), ahArg))
                    : collectArgs(frame, (TypeHandle) hType, ahArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int collectArgs(Frame frame, TypeHandle hType, ObjectHandle[] ahArg)
        {
        MethodStructure constructor = getTypeConstructor(frame, hType);
        if (constructor == null)
            {
            return reportMissingConstructor(frame, hType);
            }

        TypeConstant    typeTarget = hType.getDataType();
        TypeComposition clzTarget  = typeTarget.ensureClass(frame);
        int             nReturn    = m_nRetValue;

        if (frame.isNextRegister(nReturn))
            {
            frame.introduceResolvedVar(nReturn, typeTarget);
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, constructor.getMaxVars());
        if (anyDeferred(ahVar))
            {
            Frame.Continuation stepNext = frameCaller ->
                clzTarget.getTemplate().
                    construct(frameCaller, constructor, clzTarget, null, ahVar, nReturn);

            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

        return clzTarget.getTemplate().
            construct(frame, constructor, clzTarget, null, ahVar, nReturn);

        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argType = registerArgument(m_argType, registry);
        registerArguments(m_aArgValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argType, m_nType) + ", " +
               getParamsString(m_anArgValue, m_aArgValue);
        }

    private int   m_nType;
    private int[] m_anArgValue;

    private Argument   m_argType;
    private Argument[] m_aArgValue;
    }