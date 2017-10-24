package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Type;


/**
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends OpMove
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
        super((Argument) null, null);

        m_nToValue = nTo;
        m_nFromValue = nFrom;
        }

    /**
     * Construct a MOV op for the passed arguments.
     *
     * @param argFrom  the Argument to move from
     * @param regTo  the Register to move to
     */
    public Move(Argument argFrom, Register regTo)
        {
        super(argFrom, regTo);
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
        super(in, aconst);
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
                frame.introduceVarCopy(nFrom);
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
    }
