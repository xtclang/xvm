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
 * NEWV_0 CONSTRUCT, rvalue-type, lvalue ; virtual "new"
 */
public class NewV_0
        extends OpCallable
    {
    /**
     * Construct a NEWV_0 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argType      the type Argument
     * @param argReturn    the return Argument
     */
    public NewV_0(MethodConstant constMethod, Argument argType, Argument argReturn)
        {
        super(constMethod);

        m_argType   = argType;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewV_0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nType     = readPackedInt(in);
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
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nType);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWV_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hType = frame.getArgument(m_nType);

            return isDeferred(hType)
                    ? hType.proceed(frame,
                        frameCaller -> complete(frameCaller, (TypeHandle) frameCaller.popStack()))
                    : complete(frame, (TypeHandle) hType);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, TypeHandle hType)
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

        return clzTarget.getTemplate().
                        construct(frame, constructor, clzTarget, null, ahVar, nReturn);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argType = registerArgument(m_argType, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argType, m_nType);
        }

    private int m_nType;

    private Argument m_argType;
    }