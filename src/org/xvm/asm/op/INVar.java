package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * INVAR CONST_CLASS, CONST_STRING, rvalue-src ; (next register is an initialized named variable)
 */
public class INVar
        extends Op
    {
    /**
     * Construct an INVAR op.
     *
     * @param nTypeConstId  the type of the variable
     * @param nNameConstId  the name of the variable
     * @param nValue        the initial value for the variable
     */
    public INVar(int nTypeConstId, int nNameConstId, int nValue)
        {
        f_nClassConstId = nTypeConstId;
        f_nNameConstId  = nNameConstId;
        f_nArgValue     = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public INVar(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nClassConstId = readPackedInt(in);
        f_nNameConstId  = readPackedInt(in);
        f_nArgValue     = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_INVAR);
        writePackedLong(out, f_nClassConstId);
        writePackedLong(out, f_nNameConstId);
        writePackedLong(out, f_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_INVAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        TypeComposition clazz = context.f_types.ensureComposition(
                f_nClassConstId, frame.getActualTypes());

        try
            {
            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            frame.introduceVar(clazz, frame.getString(f_nNameConstId), Frame.VAR_STANDARD, hArg);

            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    final private int f_nClassConstId;
    final private int f_nNameConstId;
    final private int f_nArgValue;
    }
