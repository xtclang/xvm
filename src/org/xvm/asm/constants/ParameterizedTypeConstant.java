package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type of a type-parameterized class, including its type
 * parameters.
 *
 * TODO locator could return constClass iff all of the parameters are the base "Object"
 *
 * @author cp 2017.05.08
 */
public class ParameterizedTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public ParameterizedTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        int   iClass  = readIndex(in);
        int   cTypes  = readMagnitude(in);
        int[] aiConst = new int[cTypes+1];
        for (int i = 1; i <= cTypes; ++i)
            {
            aiConst[i] = readIndex(in);
            }
        aiConst[0] = iClass;
        m_aiConst  = aiConst;
        }

    /**
     * Construct a constant whose value is a parameterized class type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constClass  a ClassConstant for a parameterized class
     * @param constTypes  the type constants for the type parameters
     */
    public ParameterizedTypeConstant(ConstantPool pool, ClassConstant constClass, TypeConstant... constTypes)
        {
        super(pool);

        if (constClass == null)
            {
            throw new IllegalArgumentException("class required");
            }

        if (constTypes == null)
            {
            throw new IllegalArgumentException("type parameters required");
            }

        m_constClass = constClass;
        m_listParams = Arrays.asList(constTypes);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return a ClassConstant
     */
    public ClassConstant getClassConstant()
        {
        return m_constClass;
        }

    /**
     * @return a read-only list of type constants for the type parameters
     */
    public List<TypeConstant> getTypeConstants()
        {
        List<TypeConstant> list = m_listParams;
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ClassType;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ParameterizedTypeConstant that = (ParameterizedTypeConstant) obj;
        int n = this.m_constClass.compareTo(that.m_constClass);
        if (n == 0)
            {
            List<TypeConstant> listThis = this.m_listParams;
            List<TypeConstant> listThat = that.m_listParams;
            for (int i = 0, c = Math.min(listThis.size(), listThat.size()); i < c; ++i)
                {
                n = listThis.get(i).compareTo(listThat.get(i));
                if (n != 0)
                    {
                    return n;
                    }
                }
            n = listThis.size() - listThat.size();
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_constClass.getValueString())
          .append('<');

        boolean first = true;
        for (TypeConstant type : m_listParams)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(type.getValueString());
            }

        sb.append('>');
        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        int c = m_aiConst.length - 1;
        List<TypeConstant> listParams = new ArrayList<>(c);
        for (int i = 0; i < c; ++i)
            {
            listParams.add((TypeConstant) pool.getConstant(m_aiConst[i+1]));
            }

        m_constClass = (ClassConstant) pool.getConstant(m_aiConst[0]);
        m_listParams = listParams;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = (ClassConstant) pool.register(m_constClass);
        List<TypeConstant> listParams = m_listParams;
        for (int i = 0, c = listParams.size(); i < c; ++i)
            {
            TypeConstant constOld = listParams.get(i);
            TypeConstant constNew = (TypeConstant) pool.register(constOld);
            if (constNew != constOld)
                {
                listParams.set(i, constNew);
                }
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constClass.getPosition());
        writePackedLong(out, m_listParams.size());
        for (TypeConstant constType : m_listParams)
            {
            writePackedLong(out, constType.getPosition());
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constClass.hashCode() + m_listParams.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the class constant and the type parameters.
     */
    private int[] m_aiConst;

    /**
     * The class that is parameterized.
     */
    private ClassConstant m_constClass;

    /**
     * The type parameters.
     */
    private List<TypeConstant> m_listParams;
    }
