package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
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
 * Consider the scenario from Map.x:
 *
 *    Class Map<KeyType, ValueType>
 *        {
 *        Iterator<KeyType> iterator()
 *           {
 *           return new Iterator()
 *                {
 *                Iterator<Entry> entryIterator = Map.this.entries.iterator();
 *                ...
 *                }
 *           }
 *        }
 *
 * During validation we create a synthetic (anonymous) TypeCompositionStatement
 *
 *  class Iterator:1<Iterator:1.KeyType>
 *          implements Iterator<Iterator:1.KeyType>
 *      {
 *      ...
 *      }
 *
 * and the compile time type of the returned Iterator is Iterator:1<Map.KeyType>.
 *
 * However, it's not enough to resolve the type of "entryIterator" at the run time, since
 * the iterator's type has no knowledge of the parent's actual formal type values.
 *
 * The {@link AnonymousClassTypeConstant} represents a type assigned to that statement,
 * carrying the "parent" type information as well - in the case of the example above it would be
 * Map<KeyType, ValueType>.Iterator:1<Map.KeyType>.
 */
public class AnonymousClassTypeConstant
        extends AbstractDependantTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param idAnon      the anonymous class id
     */
    public AnonymousClassTypeConstant(ConstantPool pool, TypeConstant typeParent, ClassConstant idAnon)
        {
        super(pool, typeParent);

        if (typeParent.isAccessSpecified() ||
            typeParent.isImmutabilitySpecified() ||
            typeParent.isAnnotated())
            {
            throw new IllegalArgumentException("parent's immutability, access or annotations cannot be specified");
            }
        if (idAnon == null)
            {
            throw new IllegalArgumentException("id is required");
            }
        m_idAnon = idAnon;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public AnonymousClassTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        m_iAnon = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();

        m_idAnon = (ClassConstant) getConstantPool().getConstant(m_iAnon);
        }

    /**
     * @return the id of this {@link AnonymousClassTypeConstant}
     */
    public ClassConstant getSignature()
        {
        return m_idAnon;
        }

    /**
     * @return the anonymous class ClassStructure associated with this type
     */
    public ClassStructure getChildStructure()
        {
        return (ClassStructure) m_idAnon.getComponent();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public int getMaxParamsCount()
        {
        // anonymous classes are never formal
        return 0;
        }

    @Override
    public boolean isAnonymousClass()
        {
        return true;
        }

    @Override
    public Constant getDefiningConstant()
        {
        return getSingleUnderlyingClass(true);
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureAnonymousClassTypeConstant(type, m_idAnon);
        }

    @Override
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return false;
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        if (containsUnresolved())
            {
            return ResolutionResult.POSSIBLE;
            }

        return getChildStructure().resolveName(sName, Access.PUBLIC, collector);
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal.resolveTypedefs();
        return typeOriginal == typeResolved
                ? this
                : cloneSingle(getConstantPool(), typeResolved);
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal.resolveGenerics(pool, resolver);
        return typeOriginal == typeResolved
                ? this
                : cloneSingle(pool, typeResolved);
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        if (atypeParams == null)
            {
            // this is a "normalization" call
            atypeParams = ConstantPool.NO_TYPES;
            }

        ClassStructure clz = getChildStructure();
        if (clz.isParameterized())
            {
            return pool.ensureParameterizedTypeConstant(this,
                clz.normalizeParameters(pool, atypeParams));
            }

        // not parameterized
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
        return false;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        return getChildStructure().extendsClass(constClass);
        }

    @Override
    public Category getCategory()
        {
        return Category.CLASS;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return true;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        return getChildStructure().getIdentityConstant();
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return true;
        }

    @Override
    public Component.Format getExplicitClassFormat()
        {
        return getChildStructure().getFormat();
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return m_typeParent.containsGenericParam(sName)
            || getChildStructure().containsGenericParamType(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        ConstantPool pool = getConstantPool();
        TypeConstant type = getChildStructure().getGenericParamType(pool, sName, listParams);
        if (type != null)
            {
            return type.isGenericType()
                    ? type.resolveGenerics(pool, m_typeParent)
                    : type;
            }

        // the passed in list represents the "child" and should not be used by the parent
        return m_typeParent.getGenericParamType(sName, Collections.EMPTY_LIST);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        throw new IllegalStateException();
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        return getChildStructure().containsSubstitutableMethod(signature, access, listParams);
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return Usage.NO;
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        return registry.getTemplate((ClassConstant) getDefiningConstant());
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.AnonymousClassType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        super.forEachUnderlying(visitor);

        visitor.accept(m_idAnon);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        int n = super.compareDetails(obj);
        if (n == 0)
            {
            AnonymousClassTypeConstant that = (AnonymousClassTypeConstant) obj;

            n = this.m_idAnon.compareTo(that.m_idAnon);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_typeParent.getValueString() + '.' + m_idAnon.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_idAnon = (ClassConstant) pool.register(m_idAnon);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_idAnon.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_typeParent.hashCode() + m_idAnon.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the anonymous ClassConstant.
     */
    private transient int m_iAnon;

    /**
     * The ClassConstant representing the containing method.
     */
    protected ClassConstant m_idAnon;
    }
