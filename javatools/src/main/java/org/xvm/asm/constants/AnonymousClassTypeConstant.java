package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Consider a scenario from Map.x:
 *
 * <pre><code>
 *    interface Map&lt;Key, Value>
 *        {
 *        Iterator&lt;Key> iterator()
 *           {
 *           return new Iterator()
 *                {
 *                Iterator&lt;Entry> entryIterator = Map.this.entries.iterator();
 *                ...
 *                }
 *           }
 *        }
 * </code></pre>
 *
 * During validation we create a synthetic (anonymous) TypeCompositionStatement
 *
 * <pre><code>
 *  class Iterator:1&lt;Iterator:1.Key>
 *          implements Iterator&lt;Iterator:1.Key>
 *      {
 *      ...
 *      }
 * </code></pre>
 *
 * and the compile time type of the returned Iterator is Iterator:1&lt;Map.Key>.
 * <p/>
 * However, that information is insufficient to resolve the runtime type of the "entryIterator"
 * property inside Iterator:1, because doing so requires knowledge of the parent's (the Map's)
 * formal type values.
 * <p/>
 * The {@link AnonymousClassTypeConstant} represents a type assigned to the anonymous class itself,
 * carrying the "parent" type information. In the example above, it would be
 * {@code Map<Key, Value>.iterator().Iterator:1<Map.Key>}.
 */
public class AnonymousClassTypeConstant
        extends AbstractDependantChildTypeConstant
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

    @Override
    protected ClassStructure getChildStructure()
        {
        return (ClassStructure) m_idAnon.getComponent();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isShared(ConstantPool poolOther)
        {
        return super.isShared(poolOther) && m_idAnon.isShared(poolOther);
        }

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
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureAnonymousClassTypeConstant(type, m_idAnon);
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
    public TypeConstant[] collectGenericParameters()
        {
        // anonymous class type is not formalizable
        return null;
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


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        throw new IllegalStateException();
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
            if (!(obj instanceof AnonymousClassTypeConstant that))
                {
                return -1;
                }

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
    public int computeHashCode()
        {
        return Hash.of(m_typeParent,
               Hash.of(m_idAnon));
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