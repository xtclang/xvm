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

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CONSTR_T CONSTRUCT, rvalue-tparams
 */
public class Construct_T
        extends OpCallable
    {
    /**
     * Construct a CONSTR_T op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argValue     the tuple value Argument
     */
    public Construct_T(MethodConstant constMethod, Argument argValue)
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
    public Construct_T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nArgTupleValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CONSTR_T;
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
        MethodStructure constructor = getConstructor(frame);
        if (constructor == null)
            {
            return R_EXCEPTION;
            }

        if (constructor.isNoOp())
            {
            return R_NEXT;
            }

        ObjectHandle    hStruct = frame.getThis();
        ObjectHandle[]  ahVar   = Utils.ensureSize(ahArg, constructor.getMaxVars());

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
        return Argument.toIdString(m_argValue, m_nArgTupleValue);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
    }