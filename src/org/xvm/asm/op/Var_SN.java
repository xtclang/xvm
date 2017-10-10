package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_SN TYPE, STRING, #values:(rvalue-src) ; next register is a named initialized anonymous Sequence variable
 */
public class Var_SN
        extends Op
    {
    /**
     * Construct a VAR_SN op.
     *
     * @param nTypeConstId  the type of the sequence
     * @param anValue       the values for the sequence
     */
    public Var_SN(int nTypeConstId, int nNameConstId, int[] anValue)
        {
        f_nTypeConstId = nTypeConstId;
        f_nNameConstId = nNameConstId;
        f_anArgValue   = anValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_SN(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTypeConstId = readPackedInt(in);
        f_nNameConstId  = readPackedInt(in);
        f_anArgValue   = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_VAR_SN);
        writePackedLong(out, f_nTypeConstId);
        writePackedLong(out, f_nNameConstId);
        writeIntArray(out, f_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_SN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clazzEl = frame.f_context.f_types.ensureComposition(
                f_nTypeConstId, frame.getActualTypes());

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(f_anArgValue, f_anArgValue.length);
            if (ahArg == null)
                {
                return R_REPEAT;
                }

            ArrayHandle hArray = xArray.makeHandle(clazzEl.ensurePublicType(), ahArg);
            hArray.makeImmutable();

            frame.introduceVar(hArray.f_clazz, frame.getString(f_nNameConstId), Frame.VAR_STANDARD, hArray);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    final private int   f_nTypeConstId;
    final private int   f_nNameConstId;
    final private int[] f_anArgValue;
    }
