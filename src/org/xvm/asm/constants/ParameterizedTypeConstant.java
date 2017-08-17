package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type of a module, package, or class.
 *
 * @author cp 2017.04.25
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

        m_iType = readIndex(in);

        int cTypes  = readMagnitude(in);
        if (cTypes > 0)
            {
            int[] aiType = new int[cTypes];
            for (int i = 1; i <= cTypes; ++i)
                {
                aiType[i] = readIndex(in);
                }
            m_aiType = aiType;
            }
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param constId  a ModuleConstant, PackageConstant, or ClassConstant
     */
    public ParameterizedTypeConstant(ConstantPool pool, IdentityConstant constId,
            TypeConstant... constTypes)
        {
        super(pool);

        if (constId instanceof ThisClassConstant)
            {
            if (!constId.getName().equals(ThisClassConstant.THIS_TYPE))
                {
                throw new IllegalArgumentException("symbolic constant " + constId
                        + " is not \"" + ThisClassConstant.THIS_TYPE + "\"");
                }
            if (constTypes != null && constTypes.length > 0)
                {
                throw new IllegalArgumentException("auto-narrowing this:type can not specify type params");
                }
            }
        else if (!(constId instanceof ModuleConstant
                || constId instanceof PackageConstant
                || constId instanceof ClassConstant
                || constId instanceof PropertyConstant
                || constId instanceof TypedefConstant))
            {
            throw new IllegalArgumentException("constant " + constId.getFormat()
                    + " is not a Module, Package, Class, Typedef, or Property (formal type parameter)");
            }

        m_constId    = constId;
        m_access     = access == null ? Access.PUBLIC : access;
        m_listParams = constTypes == null || constTypes.length == 0 ? Collections.EMPTY_LIST : Arrays.asList(constTypes);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return a ModuleConstant, PackageConstant, or ClassConstant
     */
    public IdentityConstant getClassConstant()
        {
        return m_constId;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return m_constId instanceof ThisClassConstant;
        }

    /**
     * @return the access modifier for the class (public, private, etc.)
     */
    public Access getAccess()
        {
        return m_access;
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
        return Format.TerminalType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constId);
        for (Constant param : m_listParams)
            {
            visitor.accept(param);
            }
        }

    @Override
    protected Object getLocator()
        {
        return m_access == Access.PUBLIC && m_listParams.isEmpty()
                ? m_constId
                : null;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ParameterizedTypeConstant that = (ParameterizedTypeConstant) obj;
        int n = this.m_constId.compareTo(that.m_constId);
        if (n == 0)
            {
            n = this.m_access.compareTo(that.m_access);
            }

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
        sb.append(m_constId.getValueString());

        if (!m_listParams.isEmpty())
            {
            sb.append('<');

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
            }

        if (m_access != Access.PUBLIC)
            {
            sb.append(':').append(m_access.KEYWORD);
            }

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        m_constId = (IdentityConstant) pool.getConstant(m_iDef);

        if (m_aiType == null)
            {
            m_listParams = Collections.EMPTY_LIST;
            }
        else
            {
            int c = m_aiType.length;
            List<TypeConstant> listParams = new ArrayList<>(c);
            for (int i = 0; i < c; ++i)
                {
                listParams.add((TypeConstant) pool.getConstant(m_aiType[i]));
                }
            m_listParams = listParams;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constId = (IdentityConstant) pool.register(m_constId);

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
        writePackedLong(out, indexOf(m_constId));
        writePackedLong(out, m_access.ordinal());
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
        return m_constId.hashCode() + m_access.ordinal() + (m_listParams == null ? 0 : m_listParams.hashCode());
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the underlying TypeConstant.
     */
    private transient int m_iType;

    /**
     * During disassembly, this holds the index of the the type parameters.
     */
    private transient int[] m_aiType;

    /**
     * The underlying TypeConstant.
     */
    private TypeConstant m_constType;

    /**
     * The type parameters.
     */
    private List<TypeConstant> m_listParams;
    }
