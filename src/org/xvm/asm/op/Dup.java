package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;


/**
 * DUP ; (top item on stack duplicated)
 */
public class Dup
        extends OpVar
    {
    /**
     * Construct a DUP op for the specified type.
     *
     * @param constType  the type of the item on the top of the stack
     */
    public Dup(TypeConstant constType)
        {
        super(constType);
        }

    /**
     * Construct a DUP op for the specified register.
     *
     * @param reg  the register
     */
    public Dup(Register reg)
        {
        super(reg);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Dup(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_DUP;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // TODO GG

        return iPC + 1;
        }
    }
