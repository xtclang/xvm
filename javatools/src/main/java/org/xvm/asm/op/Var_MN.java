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
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.collections.xListMap;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_MN TYPE, STRING, #entries:(rvalue, rvalue) ; next register is an initialized named Map variable
 */
public class Var_MN
        extends OpVar
    {
    /**
     * Construct a VAR_MN op for the specified register, name and arguments.
     *
     * @param reg        the register
     * @param constName  the name constant
     * @param aArgVal    the key arguments
     * @param aArgVal    the value arguments
     */
    public Var_MN(Register reg, StringConstant constName, Argument[] aArgKey, Argument[] aArgVal)
        {
        super(reg);

        if (constName == null || aArgKey == null || aArgVal == null)
            {
            throw new IllegalArgumentException("name, keys and values required");
            }

        assert aArgKey.length == aArgVal.length;

        m_constName = constName;
        m_aArgKey   = aArgKey;
        m_aArgVal   = aArgVal;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_MN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId = readPackedInt(in);

        int c = readMagnitude(in);

        int[] aiKey = new int[c];
        for (int i = 0; i < c; ++i)
            {
            aiKey[i] = readPackedInt(in);
            }
        m_anArgKey = aiKey;

        int[] aiVal = new int[c];
        for (int i = 0; i < c; ++i)
            {
            aiVal[i] = readPackedInt(in);
            }
        m_anArgVal = aiVal;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constName != null)
            {
            m_nNameId  = encodeArgument(m_constName, registry);
            m_anArgKey = encodeArguments(m_aArgKey, registry);
            m_anArgVal = encodeArguments(m_aArgVal, registry);
            }

        writePackedLong(out, m_nNameId);

        int c = m_anArgKey.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, m_anArgKey[i]);
            }
        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, m_anArgVal[i]);
            }
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_MN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            TypeConstant   typeMap = frame.resolveType(m_nType);
            ObjectHandle[] ahKey   = frame.getArguments(m_anArgKey, m_anArgKey.length);
            ObjectHandle[] ahValue = frame.getArguments(m_anArgVal, m_anArgVal.length);

            frame.introduceResolvedVar(m_nVar, typeMap,
                    frame.getString(m_nNameId), Frame.VAR_STANDARD, null);

            return xListMap.INSTANCE.constructMap(frame, typeMap, ahKey, ahValue,
                    anyDeferred(ahKey), anyDeferred(ahValue), m_nVar);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        registerArguments(m_aArgKey, registry);
        registerArguments(m_aArgVal, registry);
        }

    @Override
    public String getName(Constant[] aconst)
        {
        return getName(aconst, m_constName, m_nNameId);
        }

    private int   m_nNameId;
    private int[] m_anArgKey;
    private int[] m_anArgVal;

    private StringConstant m_constName;
    private Argument[]     m_aArgKey;
    private Argument[]     m_aArgVal;
    }