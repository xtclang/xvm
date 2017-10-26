package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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
     * Construct a CONSTR_1 op.
     *
     * @param nConstructorId  identifies the construct function
     * @param nArg            r-value for the construct function argument
     *
     * @deprecated
     */
    public Construct_1(int nConstructorId, int nArg)
        {
        super(null);

        m_nFunctionId = nConstructorId;
        m_nArgValue = nArg;
        }

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
            if (hArg == null)
                {
                return R_REPEAT;
                }

            MethodStructure constructor = getMethodStructure(frame);

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];
            ahVar[0] = hArg;

            if (isProperty(hArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, constructor, ahVar);
                return new Utils.GetArgument(ahVar, stepNext).doNext(frame);
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

        registerArgument(m_argValue, registry);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }