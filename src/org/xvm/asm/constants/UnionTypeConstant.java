package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;


/**
 * Represent a constant that specifies the union ("+") of two types.
 */
public class UnionTypeConstant
        extends RelationalTypeConstant
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
    public UnionTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is the union of two types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant to union
     * @param constType2  the second TypeConstant to union
     */
    public UnionTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType1, constType2);
        }

    @Override
    protected TypeConstant cloneRelational(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return pool.ensureUnionTypeConstant(type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isNullable()
        {
        return m_constType1.isNullable() && m_constType2.isNullable();
        }

    @Override
    public TypeConstant removeNullable(ConstantPool pool)
        {
        return isNullable()
                ? pool.ensureUnionTypeConstant(m_constType1.removeNullable(pool),
                                               m_constType2.removeNullable(pool))
                : this;
        }

    @Override
    public Category getCategory()
        {
        // a union of classes is a class;
        // a union of a class and an interface is a class
        // a union of interfaces is an interface

        Category cat1 = m_constType1.getCategory();
        Category cat2 = m_constType2.getCategory();

        switch (cat1)
            {
            case CLASS:
                switch (cat2)
                    {
                    case CLASS:
                    case IFACE:
                        return Category.CLASS;

                    default:
                        return Category.OTHER;
                    }

            case IFACE:
                switch (cat2)
                    {
                    case CLASS:
                        return Category.CLASS;

                    case IFACE:
                        return Category.IFACE;

                    default:
                        return Category.OTHER;
                    }

            default:
                return Category.OTHER;
            }
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return m_constType1.isSingleUnderlyingClass(fAllowInterface)
             ^ m_constType2.isSingleUnderlyingClass(fAllowInterface);
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);

        IdentityConstant clz = m_constType1.getSingleUnderlyingClass(fAllowInterface);
        if (clz == null)
            {
            clz = m_constType2.getSingleUnderlyingClass(fAllowInterface);
            }
        return clz;
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        // for the UnionType to contribute a name, either side needs to find it
        ResolutionResult result1 = m_constType1.resolveContributedName(sName, collector);
        if (result1 == ResolutionResult.RESOLVED)
            {
            return result1;
            }

        ResolutionResult result2 = m_constType2.resolveContributedName(sName, collector);
        if (result2 == ResolutionResult.RESOLVED)
            {
            return result2;
            }

        // combine the results
        switch (result1)
            {
            case POSSIBLE:
            case DEFERRED:
            case ERROR:
                return result1;

            case UNKNOWN:
            default:
                return result2;
            }
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        TypeConstant type1 = getUnderlyingType();
        TypeConstant type2 = getUnderlyingType2();

        TypeInfo info1 = type1.ensureTypeInfo(errs);
        TypeInfo info2 = type2.ensureTypeInfo(errs);

        // +++ hack begin (this code is correct only if the formal type's constraint is Object)
        if (type1.isFormalType())
            {
            return info2;
            }
        if (type2.isFormalType())
            {
            return info1;
            }
        // --- hack end
        // TODO CP remove the hack above
        return info1;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // A + B <= A' + B' must be decomposed from the left
        if (typeLeft instanceof UnionTypeConstant || typeLeft.isAnnotated())
            {
            return super.calculateRelationToLeft(typeLeft);
            }
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.calculateRelation(typeLeft);
        Relation rel2 = thisRight2.calculateRelation(typeLeft);
        return rel1.bestOf(rel2);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        Relation rel1 = typeRight.calculateRelation(thisLeft1);
        Relation rel2 = typeRight.calculateRelation(thisLeft2);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.findIntersectionContribution(typeLeft);
        Relation rel2 = thisRight2.findIntersectionContribution(typeLeft);
        return rel1.bestOf(rel2);
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight,
                                                               Access accessLeft, List<TypeConstant> listLeft)
        {
        assert isInterfaceType();

        Set<SignatureConstant> setMiss1 =
                getUnderlyingType().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        Set<SignatureConstant> setMiss2 =
                getUnderlyingType2().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);

        setMiss1.retainAll(setMiss2); // signatures in both (intersection) are still missing
        return setMiss1;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, listParams)
            || getUnderlyingType2().containsSubstitutableMethod(signature, access, listParams);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.UnionType;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " + " + m_constType2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return "|".hashCode() ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
