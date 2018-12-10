package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.VarSupport;

import org.xvm.runtime.template.xRef.RefHandle;
import org.xvm.runtime.template.annotations.xInjectedRef;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_DN TYPE, STRING ; next register is a named "dynamic reference" variable
 */
public class Var_DN
        extends OpVar
    {
    /**
     * Construct a VAR_DN op for the specified type and name.
     *
     * @param constType  the variable type
     * @param constName  the name constant
     */
    public Var_DN(TypeConstant constType, StringConstant constName)
        {
        super(constType);

        if (constName == null)
            {
            throw new IllegalArgumentException("name required");
            }
        m_constName = constName;
        }

    /**
     * Construct a VAR_DN op for the specified register and name.
     *
     * @param reg        the register
     * @param constName  the name constant
     */
    public Var_DN(Register reg, StringConstant constName)
        {
        super(reg);

        if (constName == null)
            {
            throw new IllegalArgumentException("name required");
            }
        m_constName = constName;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_DN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constName != null)
            {
            m_nNameId = encodeArgument(m_constName, registry);
            }

        writePackedLong(out, m_nNameId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_DN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        String sName = frame.getString(m_nNameId);

        RefHandle hRef = m_ref;
        if (hRef == null)
            {
            TypeComposition clz = frame.resolveClass(m_nType);

            hRef = ((VarSupport) clz.getSupport()).createRefHandle(clz, sName);

            if (hRef instanceof xInjectedRef.InjectedHandle)
                {
                m_ref = hRef;
                }
            }

        frame.introduceResolvedVar(m_nVar, hRef.getType(), sName, Frame.VAR_DYNAMIC_REF, hRef);

        return iPC + 1;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' ' + Argument.toIdString(m_constName, m_nNameId);
        }

    private int m_nNameId;

    private StringConstant m_constName;

    /**
     * cached InjectedRef.
     * NOTE: the injected ref must be named, so this caching is not needed on the VAR_D op
     */
    transient private RefHandle m_ref;
    }
