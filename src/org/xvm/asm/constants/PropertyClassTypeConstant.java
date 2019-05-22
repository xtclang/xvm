package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A synthetic TypeConstant that represents a custom property; for example:
 * <pre>
 * class Outer
 *     {
 *     @Lazy String prop.calc()
 *         {
 *         return "hello";
 *         }
 *     static void test()
 *         {
 *         Outer o = new Outer();
 *         Ref<String> ref = o.&p;
 *         ...
 *         }
 *
 *     }
 * </pre>
 *
 * The run-time type of the variable ref above is is:
 *    PropertyClassType(T1, "prop"),
 *      where T1 is TerminalTypeConstant(Outer).
 */
public class PropertyClassTypeConstant
        extends AbstractDependantTypeConstant
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
    public PropertyClassTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        m_iProp = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param idProp      the property id
     */
    public PropertyClassTypeConstant(ConstantPool pool, TypeConstant typeParent, PropertyConstant idProp)
        {
        super(pool, typeParent);

        // unlike VirtualChildConstant, it's never unresolved
        if (typeParent.containsUnresolved() || !typeParent.isSingleUnderlyingClass(false))
            {
            throw new IllegalArgumentException("parent's must be a resolved single class type");
            }
        if (idProp == null)
            {
            throw new IllegalArgumentException("property is required");
            }

        m_idProp = idProp;
        }

    /**
     * @return the property id
     */
    public PropertyConstant getProperty()
        {
        return m_idProp;
        }

    /**
     * @return the PropertyInfo associated with this type
     */
    public PropertyInfo getPropertyInfo()
        {
        PropertyInfo info = m_info;
        if (info == null)
            {
            m_info = info = m_typeParent.ensureTypeInfo().findProperty(m_idProp);
            }
        return info;
        }

    /**
     * @return the property type
     */
    public TypeConstant getPropertyType()
        {
        return getPropertyInfo().getType();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public int getMaxParamsCount()
        {
        return 0;
        }

    @Override
    public Constant getDefiningConstant()
        {
        return getPropertyInfo().getIdentity();
        }

    @Override
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return false;
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        return ResolutionResult.UNKNOWN;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return this;
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        return this;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        return this;
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        return this;
        }

    @Override
    public boolean isNarrowedFrom(TypeConstant typeSuper, TypeConstant typeCtx)
        {
        if (typeSuper instanceof PropertyClassTypeConstant)
            {
            PropertyClassTypeConstant that = (PropertyClassTypeConstant) typeSuper;
            return this.m_idProp.equals(that.m_idProp) &&
                   this.m_typeParent.isNarrowedFrom(that.m_typeParent, typeCtx);
            }
        return false;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        PropertyConstant idProp = (PropertyConstant) getDefiningConstant();
        return idProp.getType().extendsClass(constClass);
        }

    @Override
    public Category getCategory()
        {
        return Category.OTHER;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return false;
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return getPropertyInfo().getBaseRefType().containsGenericParam(sName)
            || m_typeParent.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        TypeConstant type = getPropertyInfo().getBaseRefType().getGenericParamType(sName, listParams);
        if (type != null)
            {
            return type.isGenericType()
                    ? type.resolveGenerics(getConstantPool(), m_typeParent)
                    : type;
            }

        // the passed in list represents the "child" and should not be used by the parent
        return m_typeParent.getGenericParamType(sName, Collections.EMPTY_LIST);
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        return getPropertyType().calculateRelationToLeft(typeLeft);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        return getPropertyType().calculateRelationToRight(typeRight);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        return getPropertyType().containsSubstitutableMethod(signature, access, listParams);
        }

    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        return getPropertyInfo().getBaseRefType().getOpSupport(registry);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.PropertyClassType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        super.forEachUnderlying(visitor);

        visitor.accept(m_idProp);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        int n = super.compareDetails(obj);
        if (n == 0)
            {
            PropertyClassTypeConstant that = (PropertyClassTypeConstant) obj;
            return this.m_idProp.compareTo(that.m_idProp);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_typeParent.getValueString() + '.' + m_idProp.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        m_idProp = (PropertyConstant) getConstantPool().getConstant(m_iProp);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_idProp = (PropertyConstant) pool.register(m_idProp);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_idProp.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_typeParent.hashCode() + m_idProp.hashCode();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The PropertyConstant representing the property.
     */
    protected PropertyConstant m_idProp;

    /**
     * During disassembly, this holds the index of the PropertyConstant.
     */
    private transient int m_iProp;

    /**
     * Cached property info.
     */
    private transient PropertyInfo m_info;
    }
