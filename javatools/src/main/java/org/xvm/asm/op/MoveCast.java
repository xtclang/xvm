package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CAST rvalue-src, lvalue-dest, TYPE
 */
public class MoveCast
        extends OpMove
    {
    /**
     * Construct a CAST op for the passed arguments.
     *
     * @param argFrom  the Register to move from
     * @param argTo    the Argument to move to
     * @param typeTo   the type to cast to
     */
    public MoveCast(Argument argFrom, Argument argTo, TypeConstant typeTo)
        {
        super(argFrom, argTo);

        m_typeTo = typeTo;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveCast(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nToType = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_typeTo != null)
            {
            m_nToType = encodeArgument(m_typeTo, registry);
            }
        writePackedLong(out, m_nToType);
        }

    @Override
    public int getOpCode()
        {
        return OP_CAST;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nFromValue);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        complete(frameCaller, frameCaller.popStack()))
                    : complete(frame, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hValue)
        {
        TypeConstant typeFrom = hValue instanceof TypeHandle
                ? ((TypeHandle) hValue).getUnsafeType()
                : hValue.getType();
        TypeConstant typeTo   = frame.resolveType(m_nToType);

        if (!typeFrom.isA(typeTo))
            {
            return frame.raiseException(xException.illegalCast(frame, typeFrom.getValueString()));
            }

        if (frame.isNextRegister(m_nToValue))
            {
            frame.introduceResolvedVar(m_nToValue, typeTo);
            }

        return frame.assignValue(m_nToValue, hValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_typeTo = (TypeConstant) registerArgument(m_typeTo, registry);
        }

    @Override
    public String toString()
        {
        return super.toString() + ", " + Argument.toIdString(m_typeTo, m_nToType);
        }

    protected int m_nToType;

    private TypeConstant m_typeTo;
    }
