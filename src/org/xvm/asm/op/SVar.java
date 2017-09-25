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
 * SVAR TYPE_CONST, #values:(rvalue-src) ; next register is an initialized anonymous Sequence variable
 */
public class SVar
        extends Op
    {
    /**
     * Construct an SVAR op.
     *
     * @param nTypeConstId  the type of the sequence TODO is this Type<ElementType> or ElementType itself?
     * @param anValue       the values for the sequence
     */
    public SVar(int nTypeConstId, int[] anValue)
        {
        f_nTypeConstId = nTypeConstId;
        f_anArgValue   = anValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public SVar(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTypeConstId = readPackedInt(in);
        f_anArgValue   = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_SVAR);
        writePackedLong(out, f_nTypeConstId);
        writeIntArray(out, f_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_SVAR;
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

            frame.introduceVar(hArray.f_clazz, null, Frame.VAR_STANDARD, hArray);

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
    final private int[] f_anArgValue;
    }
