package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xClass.ClassHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEW_0G CONST-CONSTRUCT, rvalue-type, lvalue-return                             ; generic "new"
 */
public class New_0G
        extends OpCallable
    {
    /**
     * Construct a OP_NEW_0G op.
     *
     * @param nConstructorId  identifies the constructor
     * @param nType           the type of the object being created
     * @param nRet            the location to store the new object
     */
    public New_0G(int nConstructorId, int nType, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nTypeValue   = nType;
        f_nRetValue    = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public New_0G(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nConstructId = readPackedInt(in);
        f_nTypeValue   = readPackedInt(in);
        f_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_NEW_0G);
        writePackedLong(out, f_nConstructId);
        writePackedLong(out, f_nTypeValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEW_0G;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        IdentityConstant constClass = constructor.getParent().getParent().getIdentityConstant();

        try
            {
            TypeComposition clzTarget;
            if (f_nTypeValue >= 0)
                {
                ClassHandle hClass = (ClassHandle) frame.getArgument(f_nTypeValue);
                if (hClass == null)
                    {
                    return R_REPEAT;
                    }
                clzTarget = hClass.f_clazz;
                }
            else
                {
                clzTarget = frame.f_context.f_types.ensureComposition(
                        -f_nTypeValue, frame.getActualTypes());
                }

            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getMaxVars()];

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nConstructId;
    private final int f_nTypeValue;
    private final int f_nRetValue;
    }