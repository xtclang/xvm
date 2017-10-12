package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Type;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends Op
    {
    /**
     * Construct a MOV op.
     *
     * @param nFrom  the source location
     * @param nTo    the destination location
     *
     * @deprecated
     */
    public Move(int nFrom, int nTo)
        {
        m_nToValue = nTo;
        m_nFromValue = nFrom;
        }

    /**
     * Construct a MOV op for the passed arguments.
     *
     * @param argFrom  the Register to move from
     * @param regTo  the Register to move to
     */
    public Move(Argument argFrom, Register regTo)
        {
        if (argFrom == null || regTo == null)
            {
            throw new IllegalArgumentException("arguments required");
            }
        m_argFrom = argFrom;
        m_regTo   = regTo;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Move(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nFromValue = readPackedInt(in);
        m_nToValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argFrom != null)
            {
            m_nFromValue = encodeArgument(m_argFrom, registry);
            m_nToValue   = encodeArgument(m_regTo, registry);
            }

        out.writeByte(OP_MOV);
        writePackedLong(out, m_nFromValue);
        writePackedLong(out, m_nToValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            int nFrom = m_nFromValue;
            int nTo   = m_nToValue;

            ObjectHandle hValue = frame.getArgument(nFrom);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (frame.isNextRegister(nTo))
                {
                frame.copyVarInfo(nFrom);
                }
            else
                {
                Type typeFrom = hValue.m_type;
                Type typeTo   = frame.getArgumentType(nTo);

                switch (typeFrom.calculateRelation(typeTo))
                    {
                    case EQUAL:
                    case SUB:
                        // no need to do anything
                        break;

                    case SUB_WEAK:
                        // the types are assignable, but we need to inject a "safe-wrapper" proxy;
                        // for example, in the case of:
                        //      List<Object> lo;
                        //      List<String> ls = ...;
                        //      lo = ls;
                        // "add(Object o)" method needs to be wrapped on "lo" reference, to ensure the
                        // run-time type of "String"
                        throw new UnsupportedOperationException("TODO - wrap"); // TODO: wrap the handle

                    default:
                        // the compiler/verifier shouldn't have allowed this
                        throw new IllegalStateException();
                    }
                }
            return frame.assignValue(nTo, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        if (scope.isNextRegister(m_nToValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argFrom, registry);
        }

    private int m_nFromValue;
    private int m_nToValue;

    private Argument m_argFrom;
    private Register m_regTo;
    }
