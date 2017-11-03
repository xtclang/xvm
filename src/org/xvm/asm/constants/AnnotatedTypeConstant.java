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

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the annotation of another type constant.
 */
public class AnnotatedTypeConstant
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
    public AnnotatedTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iClass = readIndex(in);

        int cParams = readMagnitude(in);
        if (cParams > 0)
            {
            int[] aiParam = new int[cParams];
            for (int i = 1; i <= cParams; ++i)
                {
                aiParam[i] = readIndex(in);
                }
            m_aiParam = aiParam;
            }

        m_iType = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constClass   the class of the annotation
     * @param aconstParam  the parameters of the annotation, or null
     * @param constType    the type being annotated
     */
    public AnnotatedTypeConstant(ConstantPool pool, Constant constClass,
            Constant[] aconstParam, TypeConstant constType)
        {
        super(pool);

        if (constClass == null)
            {
            throw new IllegalArgumentException("annotation class required");
            }

        if (aconstParam != null)
            {
            checkElementsNonNull(aconstParam);
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("annotated type required");
            }

        m_constClass = constClass;
        m_listParams = aconstParam == null ? Collections.EMPTY_LIST : Arrays.asList(aconstParam);
        m_constType  = constType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the class of the annotation
     */
    public Constant getAnnotationClass()
        {
        return m_constClass;
        }

    /**
     * @return a read-only list of constants which are the parameters for the annotation
     */
    public List<Constant> getAnnotationParams()
        {
        List<Constant> list = m_listParams;
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    /**
     * @return an array of constants which are the parameters for the annotation
     */
    public Constant[] getAnnotationParamsAsArray()
        {
        List<Constant> list = m_listParams;
        return list.toArray(new Constant[list.size()]);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType;
        }

    @Override
    public boolean isAnnotated()
        {
        return true;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        return constResolved == constOriginal
                ? this
                : getConstantPool().ensureAnnotatedTypeConstant(m_constClass,
                        getAnnotationParamsAsArray(), constResolved);
        }

    @Override
    public boolean isNullable()
        {
        return m_constType.isNullable();
        }

    @Override
    public TypeConstant nonNullable()
        {
        return isNullable()
                ? getConstantPool().ensureAnnotatedTypeConstant(m_constClass,
                        getAnnotationParamsAsArray(), m_constType.nonNullable())
                : this;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.AnnotatedType;
        }

    @Override
    public Constant simplify()
        {
        m_constType = (TypeConstant) m_constType.simplify();

        List<Constant> listParams = m_listParams;
        for (int i = 0, c = listParams.size(); i < c; ++i)
            {
            Constant constOld = listParams.get(i);
            Constant constNew = constOld.simplify();
            if (constNew != constOld)
                {
                listParams.set(i, constNew);
                }
            }

        return this;
        }

    @Override
    public boolean containsUnresolved()
        {
        if (m_constClass.containsUnresolved() || m_constType.containsUnresolved())
            {
            return true;
            }
        for (Constant param : m_listParams)
            {
            if (param.containsUnresolved())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constClass);
        visitor.accept(m_constType);
        for (Constant param : m_listParams)
            {
            visitor.accept(param);
            }
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        AnnotatedTypeConstant that = (AnnotatedTypeConstant) obj;
        int n = this.m_constClass.compareTo(that.m_constClass);

        if (n == 0)
            {
            n = this.m_constType.compareTo(that.m_constType);
            }

        Params: if (n == 0)
            {
            List<Constant> listThis = this.m_listParams;
            List<Constant> listThat = that.m_listParams;
            for (int i = 0, c = Math.min(listThis.size(), listThat.size()); i < c; ++i)
                {
                n = listThis.get(i).compareTo(listThat.get(i));
                if (n != 0)
                    {
                    break Params;
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

        sb.append('@')
          .append(m_constClass.getValueString());

        if (!m_listParams.isEmpty())
            {
            sb.append('(');

            boolean first = true;
            for (Constant param : m_listParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.getValueString());
                }

            sb.append(')');
            }

        sb.append(' ')
          .append(m_constType.getValueString());

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        m_constClass = (ClassConstant) pool.getConstant(m_iClass);

        if (m_aiParam == null)
            {
            m_listParams = Collections.EMPTY_LIST;
            }
        else
            {
            int c = m_aiParam.length;
            List<Constant> listParams = new ArrayList<>(c);
            for (int i = 0; i < c; ++i)
                {
                listParams.add(pool.getConstant(m_aiParam[i]));
                }
            m_listParams = listParams;
            }

        m_constType = (TypeConstant) pool.getConstant(m_iType);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = pool.register(m_constClass);

        List<Constant> listParams = m_listParams;
        for (int i = 0, c = listParams.size(); i < c; ++i)
            {
            Constant constOld = listParams.get(i);
            Constant constNew = pool.register(constOld);
            if (constNew != constOld)
                {
                listParams.set(i, constNew);
                }
            }

        m_constType = (TypeConstant) pool.register(m_constType);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constClass));
        writePackedLong(out, m_listParams.size());
        for (Constant param : m_listParams)
            {
            writePackedLong(out, param.getPosition());
            }
        writePackedLong(out, indexOf(m_constType));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constClass.hashCode() ^ m_listParams.hashCode() ^ m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the class constant of the annotation.
     */
    private int m_iClass;

    /**
     * During disassembly, this holds the index of the the annotation parameters.
     */
    private int[] m_aiParam;

    /**
     * During disassembly, this holds the index of the type constant of the type being annotated.
     */
    private int m_iType;

    /**
     * The annotating class.
     */
    private Constant m_constClass;

    /**
     * The annotation parameters.
     */
    private List<Constant> m_listParams;

    /**
     * The type being annotated.
     */
    private TypeConstant m_constType;
    }
