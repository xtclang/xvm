package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.runtime.Frame;


/**
 * VAR TYPE ; (next register is an uninitialized anonymous variable)
 */
public class Var
        extends OpVar
    {
    /**
     * Construct a VAR op.
     *
     * @param nType  the variable type id
     *
     * @deprecated
     */
    public Var(int nType)
        {
        super(null);

        m_nType = nType;
        }

    /**
     * Construct a VAR op for the specified type.
     *
     * @param constType  the variable type
     */
    public Var(TypeConstant constType)
        {
        super(constType);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.introduceVar(convertId(m_nType), 0, Frame.VAR_STANDARD, null);

        return iPC + 1;
        }
    }
