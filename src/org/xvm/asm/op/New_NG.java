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
 *  NEW_NG CONST-CONSTRUCT, rvalue-type, #params:(rvalue), lvalue-return
 */
public class New_NG
        extends OpCallable
    {
    /**
     * Construct a NEW_NG op.
     *
     * @param nConstructorId  identifies the constructor
     * @param nType           the type of the object being created
     * @param anArg           the constructor arguments
     * @param nRet            the location to store the new object
     */
    public New_NG(int nConstructorId, int nType, int[] anArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nTypeValue   = nType;
        f_anArgValue   = anArg;
        f_nRetValue    = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public New_NG(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nConstructId = readPackedInt(in);
        f_nTypeValue   = readPackedInt(in);
        f_anArgValue   = readIntArray(in);
        f_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_NEW_NG);
        writePackedLong(out, f_nConstructId);
        writePackedLong(out, f_nTypeValue);
        writeIntArray(out, f_anArgValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEW_NG;
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

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nConstructId;
    private final int   f_nTypeValue;
    private final int[] f_anArgValue;
    private final int   f_nRetValue;
    }
