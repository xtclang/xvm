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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CONSTR_N CONST-CONSTRUCT, #params:(rvalue)
 */
public class Construct_N
        extends OpCallable
    {
    /**
     * Construct a OP_CONSTR_N op.
     *
     * @param nConstructorId  identifies the construct function
     * @param anArg           r-values for the construct function arguments
     */
    public Construct_N(int nConstructorId, int[] anArg)
        {
        f_nConstructId = nConstructorId;
        f_anArgValue   = anArg;
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
        f_nConstructId = readPackedInt(in);
        f_anArgValue   = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_CONSTR_N);
        writePackedLong(out, f_nConstructId);
        writeIntArray(out, f_anArgValue);
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
            MethodStructure constructor = getMethodStructure(frame, f_nConstructId);

            ObjectHandle hStruct = frame.getThis();
            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }
            frame.chainFinalizer(hStruct.f_clazz.f_template.makeFinalizer(constructor, hStruct, ahVar));

            return frame.call1(constructor, hStruct, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nConstructId;
    private final int[] f_anArgValue;
    }