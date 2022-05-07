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
import org.xvm.runtime.Utils;


/**
 * CONSTR_N CONSTRUCT, #params:(rvalue)
 */
public class Construct_N
        extends OpCallable
    {
    /**
     * Construct a CONSTR_N op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param aArgValue       the array of value Arguments
     */
    public Construct_N(MethodConstant constMethod, Argument[] aArgValue)
        {
        super(constMethod);

        m_aArgValue = aArgValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Construct_N(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            }

        writeIntArray(out, m_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CONSTR_N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            MethodStructure constructor = getConstructor(frame);
            if (constructor == null)
                {
                return R_EXCEPTION;
                }

            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, constructor.getMaxVars());

            if (anyDeferred(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, constructor, ahVar);
                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

            return complete(frame, constructor, ahVar);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, MethodStructure constructor, ObjectHandle[] ahVar)
        {
        if (constructor.isNoOp())
            {
            return R_NEXT;
            }

        ObjectHandle hStruct = frame.getThis();

        frame.chainFinalizer(Utils.makeFinalizer(frame, constructor, ahVar));

        return frame.call1(constructor, hStruct, ahVar, A_IGNORE);
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