package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;


/**
 * CONSTR_N CONST-CONSTRUCT, #params:(rvalue)
 */
public class Construct_N
        extends OpCallable
    {
    /**
     * Construct a CONSTR_N op.
     *
     * @param nConstructorId  identifies the construct function
     * @param anArg           r-values for the construct function arguments
     *
     * @deprecated
     */
    public Construct_N(int nConstructorId, int[] anArg)
        {
        super(nConstructorId);

        m_anArgValue = anArg;
        }

    /**
     * Construct a CONSTR_N op based on the passed arguments.
     *
     * @param argConstructor  the constructor Argument
     * @param aArgValue       the array of value Arguments
     */
    public Construct_N(Argument argConstructor, Argument[] aArgValue)
        {
        super(argConstructor);

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
        super(readPackedInt(in));

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
            MethodStructure constructor = getMethodStructure(frame);

            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (anyProperty(ahVar))
                {
                Frame.Continuation stepLast = frameCaller ->
                    complete(frameCaller, constructor, ahVar);
                return new Utils.GetArgument(ahVar, stepLast).doNext(frame);
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
        ObjectHandle hStruct = frame.getThis();

        frame.chainFinalizer(Utils.makeFinalizer(constructor, hStruct, ahVar));

        return frame.call1(constructor, hStruct, ahVar, Frame.RET_UNUSED);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }