package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * PIP_INCA PROPERTY, rvalue, lvalue ; same as IP_INCA for a register
 */
public class PIP_PostInc
        extends OpProperty
    {
    /**
     * Construct a PIP_INCA op.
     *
     * @param nPropId  the property to increment
     * @param nTarget  the object on which the property exists
     * @param nRet     the location to store the post-incremented value
     */
    public PIP_PostInc(int nPropId, int nTarget, int nRet)
        {
        f_nPropConstId = nPropId;
        f_nTarget      = nTarget;
        f_nRetValue    = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_PostInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nPropConstId = readPackedInt(in);
        f_nTarget      = readPackedInt(in);
        f_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_PIP_INCA);
        writePackedLong(out, f_nPropConstId);
        writePackedLong(out, f_nTarget);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_PIP_INCA;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            PropertyConstant constProperty = (PropertyConstant)
                    frame.f_context.f_pool.getConstant(f_nPropConstId);

            return hTarget.f_clazz.f_template.invokePostInc(
                    frame, hTarget, constProperty.getName(), f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nPropConstId;
    private final int f_nTarget;
    private final int f_nRetValue;
    }