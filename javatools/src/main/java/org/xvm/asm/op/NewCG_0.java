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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWCG_0 CONSTRUCT, rvalue-parent, TYPE, lvalue ; generic-type "new virtual child"
 */
public class NewCG_0
        extends OpCallable
    {
    /**
     * Construct a NEWCG_0 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argParent    the parent Argument
     * @param argType      the type Argument
     * @param argReturn    the return Argument
     */
    public NewCG_0(MethodConstant constMethod, Argument argParent, Argument argType, Argument argReturn)
        {
        super(constMethod);

        m_argParent = argParent;
        m_argType   = argType;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewCG_0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nParentValue = readPackedInt(in);
        m_nTypeValue   = readPackedInt(in);
        m_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argParent != null)
            {
            m_nParentValue = encodeArgument(m_argParent, registry);
            m_nTypeValue   = encodeArgument(m_argType, registry);
            m_nRetValue    = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nParentValue);
        writePackedLong(out, m_nTypeValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEWCG_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hParent = frame.getArgument(m_nParentValue);

            return isDeferred(hParent)
                    ? hParent.proceed(frame, frameCaller ->
                        complete(frameCaller, frameCaller.popStack()))
                    : complete(frame, hParent);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hParent)
        {
        MethodStructure constructor = getChildConstructor(frame, hParent);
        if (constructor == null)
            {
            return reportMissingConstructor(frame, hParent);
            }

        TypeConstant typeChild = frame.resolveType(m_nTypeValue);

        ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];
        return constructChild(frame, constructor, hParent, typeChild, ahVar);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argParent = registerArgument(m_argParent, registry);
        m_argType   = registerArgument(m_argType, registry);
        }

    private int m_nParentValue;
    private int m_nTypeValue;

    private Argument m_argParent;
    private Argument m_argType;
    }