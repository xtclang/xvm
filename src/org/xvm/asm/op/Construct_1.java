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
 * CONSTR_1 CONST-CONSTRUCT, rvalue
 */
public class Construct_1
        extends OpCallable
    {
    /**
     * Construct a OP_CONSTR_1 op.
     *
     * @param nConstructorId  identifies the construct function
     * @param nArg            r-value for the construct function argument
     */
    public Construct_1(int nConstructorId, int nArg)
        {
        f_nConstructId  = nConstructorId;
        f_nArgValue     = nArg;
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
        f_nConstructId  = readPackedInt(in);
        f_nArgValue     = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_CONSTR_1);
        writePackedLong(out, f_nConstructId);
        writePackedLong(out, f_nArgValue);
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
            MethodStructure constructor = getMethodStructure(frame, f_nConstructId);

            ObjectHandle hStruct = frame.getThis();
            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];
            ahVar[0] = hArg;

            frame.chainFinalizer(hStruct.f_clazz.f_template.makeFinalizer(constructor, hStruct, ahVar));

            return frame.call1(constructor, hStruct, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nConstructId;
    private final int f_nArgValue;
    }