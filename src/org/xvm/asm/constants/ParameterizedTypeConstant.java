package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Type;
import org.xvm.runtime.TypeSet;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type of a module, package, or class.
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
            m_aiTypeParams = aiType;
            }
        }

    /**
     * Construct a constant whose value is a type-parameterized data type.
     *
     * @param pool             the ConstantPool that will contain this Constant
     * @param constType        a TypeConstant representing the parameterized type
     * @param constTypeParams  a number of TypeConstants representing the type parameters
     */
    public ParameterizedTypeConstant(ConstantPool pool, TypeConstant constType,
            TypeConstant... constTypeParams)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }
        if (constType.isParamsSpecified())
            {
            throw new IllegalArgumentException("type is already parameterized");
            }
        if (!constType.isSingleDefiningConstant())
            {
            throw new IllegalArgumentException("type must refer to a single underlying class");
            }

        m_constType      = constType;
        m_listTypeParams = constTypeParams == null || constTypeParams.length == 0
                ? Collections.EMPTY_LIST
                : Arrays.asList(constTypeParams);
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
    public boolean isParamsSpecified()
        {
        return true;
        }

    @Override
    public List<TypeConstant> getParamTypes()
        {
        List<TypeConstant> list = m_listTypeParams;
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        boolean      fDiff         = constOriginal != constResolved;

        List<TypeConstant> listOriginal   = m_listTypeParams;
        TypeConstant[]     aconstResolved = null;
        for (int i = 0, c = listOriginal.size(); i < c; ++i)
            {
            TypeConstant constParamOriginal = listOriginal.get(i);
            TypeConstant constParamResolved = constParamOriginal.resolveTypedefs();
            if (fDiff || constParamOriginal != constParamResolved)
                {
                if (aconstResolved == null)
                    {
                    aconstResolved = listOriginal.toArray(new TypeConstant[c]);
                    }
                aconstResolved[i] = constParamResolved;
                fDiff = true;
                }
            }

        return fDiff
                ? getConstantPool().ensureParameterizedTypeConstant(constResolved, aconstResolved)
                : this;
        }

    @Override
    public boolean consumesFormalType(String sTypeName, TypeSet types, Access access)
        {
        // C<T> = A<B<T>> consumes T iff
        //  1) A consumes A_Type && B produces B_Type, or
        //  2) A produces A_Type && B consumes B_Type

        ClassConstant constClass = (ClassConstant) m_constType.getIdentityConstant();
        ClassTemplate template = types.getTemplate(constClass);
        ListMap<String, Type> mapFormal = template.f_mapGenericFormal;

        List<TypeConstant> list = m_listTypeParams;
        assert  mapFormal.size() == list.size(); // what about Tuple?

        Iterator<TypeConstant> iterParams = list.iterator();
        Iterator<String> iterNames = mapFormal.keySet().iterator();
        while (iterParams.hasNext())
            {
            TypeConstant constParam = iterParams.next();
            String sFormal = iterNames.next();

            if (template.consumesFormalType(sFormal, access)
                    && constParam.producesFormalType(sTypeName, types, access)
                ||
                template.producesFormalType(sFormal, access)
                    && constParam.consumesFormalType(sTypeName, types, access))
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public boolean producesFormalType(String sTypeName, TypeSet types, Access access)
        {
        // C<T> = A<B<T>> produces T iff
        //  1) A produces A_Type && B produces B_Type, or
        //  2) A consumes A_Type && B consumes B_Type

        ClassConstant constClass = (ClassConstant) m_constType.getIdentityConstant();
        ClassTemplate template = types.getTemplate(constClass);
        ListMap<String, Type> mapFormal = template.f_mapGenericFormal;

        List<TypeConstant> list = m_listTypeParams;
        assert  mapFormal.size() == list.size(); // what about Tuple?

        Iterator<TypeConstant> iterParams = list.iterator();
        Iterator<String> iterNames = mapFormal.keySet().iterator();
        while (iterParams.hasNext())
            {
            TypeConstant constParam = iterParams.next();
            String sFormal = iterNames.next();

            if (template.producesFormalType(sFormal, access)
                    && constParam.producesFormalType(sTypeName, types, access)
                ||
                template.consumesFormalType(sFormal, access)
                    && constParam.consumesFormalType(sTypeName, types, access))
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public boolean isConstant()
        {
        for (TypeConstant type : m_listTypeParams)
            {
            if (!type.isConstant())
                {
                return false;
                }
            }

        return super.isConstant();
        }

    @Override
    public boolean isNullable()
        {
        assert !m_constType.isNullable();
        return false;
        }

    @Override
    public boolean isCongruentWith(TypeConstant that)
        {
        that = that.unwrapForCongruence();
        if (that instanceof ParameterizedTypeConstant)
            {
            ParameterizedTypeConstant thatP = (ParameterizedTypeConstant) that;
            if (this.m_constType.isCongruentWith(thatP.m_constType))
                {
                List<TypeConstant> listThis = this.m_listTypeParams;
                List<TypeConstant> listThat = thatP.m_listTypeParams;
                int cParams = listThis.size();
                if (listThat.size() == cParams)
                    {
                    for (int i = 0, c = listThis.size(); i < c; ++i)
                        {
                        if (!listThis.get(i).isCongruentWith(listThat.get(i)))
                            {
                            return false;
                            }
                        }
                    return true;
                    }
                }
            }

        return false;
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParameterizedType;
        }

    @Override
    public boolean containsUnresolved()
        {
        if (m_constType.containsUnresolved())
            {
            return true;
            }
        for (Constant param : m_listTypeParams)
            {
            if (param.containsUnresolved())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public Constant simplify()
        {
        m_constType = (TypeConstant) m_constType.simplify();

        List<TypeConstant> listParams = m_listTypeParams;
        for (int i = 0, c = listParams.size(); i < c; ++i)
            {
            TypeConstant constOld = listParams.get(i);
            TypeConstant constNew = (TypeConstant) constOld.simplify();
            if (constNew != constOld)
                {
                listParams.set(i, constNew);
                }
            }

        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        for (Constant param : m_listTypeParams)
            {
            visitor.accept(param);
            }
        }

    @Override
    protected Object getLocator()
        {
        return m_listTypeParams.isEmpty()
                ? m_constType
                : null;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ParameterizedTypeConstant that = (ParameterizedTypeConstant) obj;
        int n = this.m_constType.compareTo(that.m_constType);
        if (n == 0)
            {
            List<TypeConstant> listThis = this.m_listTypeParams;
            List<TypeConstant> listThat = that.m_listTypeParams;
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
        sb.append(m_constType.getValueString());

        if (!m_listTypeParams.isEmpty())
            {
            sb.append('<');

            boolean first = true;
            for (TypeConstant type : m_listTypeParams)
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

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        m_constType = (TypeConstant) pool.getConstant(m_iType);

        if (m_aiTypeParams == null)
            {
            m_listTypeParams = Collections.EMPTY_LIST;
            }
        else
            {
            int c = m_aiTypeParams.length;
            List<TypeConstant> listParams = new ArrayList<>(c);
            for (int i = 0; i < c; ++i)
                {
                listParams.add((TypeConstant) pool.getConstant(m_aiTypeParams[i]));
                }
            m_listTypeParams = listParams;
            m_aiTypeParams   = null;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);

        List<TypeConstant> listParams = m_listTypeParams;
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
        writePackedLong(out, indexOf(m_constType));
        writePackedLong(out, m_listTypeParams.size());
        for (TypeConstant constType : m_listTypeParams)
            {
            writePackedLong(out, constType.getPosition());
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constType.hashCode() + m_listTypeParams.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the underlying TypeConstant.
     */
    private transient int m_iType;

    /**
     * During disassembly, this holds the index of the the type parameters.
     */
    private transient int[] m_aiTypeParams;

    /**
     * The underlying TypeConstant.
     */
    private TypeConstant m_constType;

    /**
     * The type parameters.
     */
    private List<TypeConstant> m_listTypeParams;
    }
