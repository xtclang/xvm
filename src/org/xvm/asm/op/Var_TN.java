package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Type;
import org.xvm.runtime.TypeSet;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_TN STRING, #values:(TYPE, rvalue-src) ; next register is an initialized anonymous Tuple variable
 */
public class Var_TN
        extends OpVar
    {
    /**
     * Construct a VAR_TN op.
     *
     * @param nType      the type id of the Tuple variable
     * @param nNameId    the name of the variable id
     * @param anValueId  the value ids for the sequence
     *
     * @deprecated
     */
    public Var_TN(int nType, int nNameId, int[] anValueId)
        {
        super(null);

        m_nType = nType;
        m_nNameId = nNameId;
        m_anArgValue = anValueId;
        }

    /**
     * Construct a VAR_TN op for the specified type, name and arguments.
     *
     * @param constType the variable type
     * @param constName  the name constant
     * @param aArgValue  the value argument
     */
    public Var_TN(TypeConstant constType, StringConstant constName, Argument[] aArgValue)
        {
        super(constType);

        if (constName == null || aArgValue == null)
            {
            throw new IllegalArgumentException("name and values required");
            }

        m_constName = constName;
        m_aArgValue = aArgValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_TN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId = readPackedInt(in);
        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constName != null)
            {
            m_nNameId = encodeArgument(m_constName, registry);
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            }

        writePackedLong(out, m_nNameId);
        writeIntArray(out, m_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_TN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeConstant constType = (TypeConstant) frame.getConstant(m_nType);
        assert constType.isParamsSpecified();

        int cArgs = m_anArgValue.length;
        List<TypeConstant> listTypes = constType.getParamTypes();
        assert listTypes.size() == cArgs;

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, cArgs);
            if (ahArg == null)
                {
                return R_REPEAT;
                }

            TypeSet           types     = frame.f_context.f_types;
            Map<String, Type> mapActual = frame.getActualTypes();

            Type[] aType = new Type[cArgs];
            for (int i = 0; i < cArgs; i++)
                {
                aType[i] = types.resolveType(listTypes.get(i), mapActual);
                }

            TupleHandle hTuple = xTuple.makeHandle(aType, ahArg);

            frame.introduceVar(convertId(m_nType), m_nNameId, Frame.VAR_STANDARD, hTuple);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    private int m_nNameId;
    private int[] m_anArgValue;

    private StringConstant m_constName;
    private Argument[] m_aArgValue;
    }
