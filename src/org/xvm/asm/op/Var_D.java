package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * VAR_D TYPE ; next register is an anonymous "dynamic reference" variable
 */
public class Var_D
        extends OpVar
    {
    /**
     * Construct a VAR_D.
     *
     * @param nType  the variable type id
     *
     * @deprecated
     */
    public Var_D(int nType)
        {
        super();

        m_nType = nType;
        }

    /**
     * Construct a VAR_D op for the specified type.
     *
     * @param constType  the variable type
     */
    public Var_D(TypeConstant constType)
        {
        super(constType);
        }

    /**
     * Construct a VAR_D op for the specified register.
     *
     * @param reg  the register
     */
    public Var_D(Register reg)
        {
        super(reg);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_D(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_D;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clz = frame.resolveClass(m_nType);

        RefHandle hRef = clz.getTemplate().createRefHandle(clz, null);

        frame.introduceResolvedVar(clz.getType(), null, Frame.VAR_DYNAMIC_REF, hRef);

        return iPC + 1;
        }
    }
