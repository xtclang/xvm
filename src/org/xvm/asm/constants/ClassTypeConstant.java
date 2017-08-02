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
public class ClassTypeConstant
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
    public ClassTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iClass = readIndex(in);
        m_access = Access.valueOf(readIndex(in));

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
    public ClassTypeConstant(ConstantPool pool, IdentityConstant constId, Access access, TypeConstant... constTypes)
        {
        super(pool);

        if (constId instanceof SymbolicConstant)
            {
            if (!constId.getName().equals(SymbolicConstant.THIS_TYPE))
                {
                throw new IllegalArgumentException("symbolic constant " + constId
                        + " is not \"" + SymbolicConstant.THIS_TYPE + "\"");
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
        return m_constId instanceof SymbolicConstant;
        }

    @Override
    public boolean isIdentity(IdentityConstant constId)
        {
        return m_constId.equals(constId) && m_access == Access.PUBLIC;
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
        return Format.ClassType;
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
        ClassTypeConstant that = (ClassTypeConstant) obj;
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

        m_constId = (IdentityConstant) pool.getConstant(m_iClass);

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
     * During disassembly, this holds the index of the module, package, or class constant.
     */
    private transient int m_iClass;

    /**
     * During disassembly, this holds the index of the the type parameters.
     */
    private transient int[] m_aiType;

    /**
     * The class referred to. May be a ModuleConstant, PackageConstant, or ClassConstant.
     */
    private IdentityConstant m_constId;

    /**
     * The public/private/etc. modifier for the class referred to.
     */
    private Access m_access;

    /**
     * The type parameters.
     */
    private List<TypeConstant> m_listParams;
    }
