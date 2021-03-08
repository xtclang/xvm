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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CONSTR_1 CONSTRUCT, rvalue
 */
public class Construct_1
        extends OpCallable
    {
    /**
     * Construct a CONSTR_1 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argValue        the value Argument
     */
    public Construct_1(MethodConstant constMethod, Argument argValue)
        {
        super(constMethod);

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Construct_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CONSTR_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArgValue);

            MethodStructure constructor = getConstructor(frame);
            if (constructor == null)
                {
                return R_EXCEPTION;
                }

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];
            ahVar[0] = hArg;

            if (isDeferred(hArg))
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

        frame.chainFinalizer(Utils.makeFinalizer(constructor, ahVar));

        return frame.call1(constructor, hStruct, ahVar, A_IGNORE);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argValue, m_nArgValue);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }