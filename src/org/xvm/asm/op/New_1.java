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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEW_1 CONST-CONSTRUCT, rvalue-param, lvalue-return
 */
public class New_1
        extends OpCallable
    {
    /**
     * Construct a NEW_1 op.
     *
     * @param nConstructorId  identifies the constructor
     * @param nArg            the constructor argument
     * @param nRet            the location to store the new object
     */
    public New_1(int nConstructorId, int nArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nArgValue    = nArg;
        f_nRetValue    = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public New_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nConstructId = readPackedInt(in);
        f_nArgValue    = readPackedInt(in);
        f_nRetValue    = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_NEW_1);
        writePackedLong(out, f_nConstructId);
        writePackedLong(out, f_nArgValue);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NEW_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        IdentityConstant constClass = constructor.getParent().getParent().getIdentityConstant();

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(
                    new int[]{f_nArgValue}, constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            return template.construct(frame, constructor,
                    template.f_clazzCanonical, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nConstructId;
    private final int f_nArgValue;
    private final int f_nRetValue;
    }
