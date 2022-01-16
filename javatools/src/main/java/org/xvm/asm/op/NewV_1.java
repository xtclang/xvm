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

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWV_1 CONSTRUCT, rvalue-type, rvalue-param, lvalue; virtual "new"
 */
public class NewV_1
        extends OpCallable
    {
    /**
     * Construct a NEWV_1 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argType      the type Argument
     * @param argValue     the value Argument
     * @param argReturn    the return Argument
     */
    public NewV_1(MethodConstant constMethod, Argument argType, Argument argValue, Argument argReturn)
        {
        super(constMethod);

        m_argType   = argType;
        m_argValue  = argValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewV_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nType     = readPackedInt(in);
        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argType != null)
            {
            m_nType     = encodeArgument(m_argType, registry);
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nType);
        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWV_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hType = frame.getArgument(m_nType);
            ObjectHandle hArg  = frame.getArgument(m_nArgValue);

            return isDeferred(hType)
                    ? hType.proceed(frame, frameCaller ->
                        collectArg(frameCaller, (TypeHandle) frameCaller.popStack(), hArg))
                    : collectArg(frame, (TypeHandle) hType, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int collectArg(Frame frame, TypeHandle hType, ObjectHandle hArg)
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

        ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];
        ahVar[0] = hArg;

        return isDeferred(hArg)
                ? hArg.proceed(frame, frameCaller ->
                    {
                    ahVar[0] = frameCaller.popStack();
                    return clzTarget.getTemplate().
                        construct(frameCaller, constructor, clzTarget, null, ahVar, nReturn);
                    })
                : clzTarget.getTemplate().
                        construct(frame, constructor, clzTarget, null, ahVar, nReturn);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argType  = registerArgument(m_argType, registry);
        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argType, m_nType) + ", " +
               Argument.toIdString(m_argValue, m_nArgValue);
        }

    private int m_nType;
    private int m_nArgValue;

    private Argument m_argType;
    private Argument m_argValue;
    }