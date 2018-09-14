package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;


/**
 * Represent a constant that specifies the intersection ("|") of two types.
 */
public class IntersectionTypeConstant
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
    public IntersectionTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is the intersection of two types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant to intersect
     * @param constType2  the second TypeConstant to intersect
     */
    public IntersectionTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType1, constType2);
        }

    @Override
    protected TypeConstant cloneRelational(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return pool.ensureIntersectionTypeConstant(type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isNullable()
        {
        return (m_constType1.isOnlyNullable() ^ m_constType2.isOnlyNullable())
                || m_constType1.isNullable() || m_constType2.isNullable();
        }

    @Override
    public TypeConstant removeNullable(ConstantPool pool)
        {
        if (!isNullable())
            {
            return this;
            }

        if (m_constType1.isOnlyNullable())
            {
            assert !m_constType2.isOnlyNullable();
            return m_constType2.removeNullable(pool);
            }

        if (m_constType2.isOnlyNullable())
            {
            assert !m_constType1.isOnlyNullable();
            return m_constType1.removeNullable(pool);
            }

        return pool.ensureIntersectionTypeConstant(m_constType1.removeNullable(pool),
                                                   m_constType2.removeNullable(pool));
        }

    @Override
    public Category getCategory()
        {
        // an intersection of classes is a class;
        // an intersection of interfaces is an interface

        Category cat1 = m_constType1.getCategory();
        Category cat2 = m_constType2.getCategory();

        switch (cat1)
            {
            case CLASS:
                switch (cat2)
                    {
                    case CLASS:
                        return Category.CLASS;

                    default:
                        return Category.OTHER;
                    }

            case IFACE:
                switch (cat2)
                    {
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
            && m_constType2.isSingleUnderlyingClass(fAllowInterface)
            && m_constType1.getSingleUnderlyingClass(fAllowInterface).equals(
               m_constType2.getSingleUnderlyingClass(fAllowInterface));
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);
        return m_constType1.getSingleUnderlyingClass(fAllowInterface);
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        // for the IntersectionType to contribute a name, both sides need to find exactly
        // the same component
        SimpleCollector  collector1 = new SimpleCollector();
        ResolutionResult result1    = m_constType1.resolveContributedName(sName, collector1);
        if (result1 != ResolutionResult.RESOLVED)
            {
            return result1;
            }

        SimpleCollector  collector2 = new SimpleCollector();
        ResolutionResult result2    = m_constType2.resolveContributedName(sName, collector2);
        if (result2 != ResolutionResult.RESOLVED)
            {
            return result2;
            }

        Constant const1 = collector1.getResolvedConstant();
        Constant const2 = collector2.getResolvedConstant();

        if (const1.equals(const2))
            {
            collector.resolvedConstant(const1);
            return ResolutionResult.RESOLVED;
            }
        return ResolutionResult.UNKNOWN; // ambiguous
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // we've been asked to resolve some type defined as "T1 | T2";  first, resolve T1 and T2
        TypeInfo info1 = getUnderlyingType().ensureTypeInfo(errs);
        TypeInfo info2 = getUnderlyingType2().ensureTypeInfo(errs);
        // TODO CP
        return getConstantPool().typeObject().ensureTypeInfo(errs);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.calculateRelation(typeLeft);
        Relation rel2 = thisRight2.calculateRelation(typeLeft);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // A | B <= A' | B' must have been decomposed from the right
        assert !(typeRight instanceof IntersectionTypeConstant);

        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        Relation rel1 = typeRight.calculateRelation(thisLeft1);
        Relation rel2 = typeRight.calculateRelation(thisLeft2);
        Relation rel  = rel1.bestOf(rel2);
        if (rel == Relation.INCOMPATIBLE)
            {
            // to deal with a scenario of A | B <= M [+ X], where M is a mixin into A' | B'
            // should be looked at holistically, without immediate decomposition;
            // since a mixin is the only (for purposes of "isA" decision making) terminal type
            // that allows an intersection as an "into" contribution
            return typeRight.findIntersectionContribution(this);
            }
        return rel;
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        Relation rel1 = thisRight1.findIntersectionContribution(typeLeft);
        Relation rel2 = thisRight2.findIntersectionContribution(typeLeft);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(
            TypeConstant typeRight, Access accessLeft, List<TypeConstant> listLeft)
        {
        assert isInterfaceType();

        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        Set<SignatureConstant> setMiss1 = thisLeft1.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        if (setMiss1.isEmpty())
            {
            return setMiss1; // type1 is assignable from that
            }

        Set<SignatureConstant> setMiss2 = thisLeft2.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        if (setMiss2.isEmpty())
            {
            return setMiss2; // type2 is assignable from that
            }

        // neither is assignable; merge the misses
        if (setMiss2 != null)
            {
            setMiss1.addAll(setMiss2);
            }
        return setMiss1;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, listParams)
            && getUnderlyingType2().containsSubstitutableMethod(signature, access, listParams);
        }

    @Override
    public boolean isIntoClassType()
        {
        return getUnderlyingType().isIntoClassType()
            || getUnderlyingType2().isIntoClassType();
        }

    @Override
    public boolean isIntoPropertyType()
        {
        return getUnderlyingType().isIntoPropertyType()
            || getUnderlyingType2().isIntoPropertyType();
        }

    @Override
    public TypeConstant getIntoPropertyType()
        {
        TypeConstant typeInto1 = getUnderlyingType().getIntoPropertyType();
        TypeConstant typeInto2 = getUnderlyingType2().getIntoPropertyType();

        if (typeInto1 == null)
            {
            return typeInto2;
            }

        if (typeInto2 == null)
            {
            return typeInto1;
            }

        ConstantPool pool     = getConstantPool();
        TypeConstant typeProp = pool.typeProperty();

        if (typeInto1.equals(typeProp) || typeInto2.equals(typeProp))
            {
            return typeProp;
            }

        TypeConstant typeVar = pool.typeVar();
        if (typeInto1.equals(typeVar) || typeInto2.equals(typeVar))
            {
            return typeVar;
            }

        return pool.typeRef();
        }

    @Override
    public boolean isIntoMethodType()
        {
        return getUnderlyingType().isIntoMethodType()
            || getUnderlyingType2().isIntoMethodType();
        }

    @Override
    public boolean isIntoVariableType()
        {
        return getUnderlyingType().isIntoVariableType()
            || getUnderlyingType2().isIntoVariableType();
        }

    @Override
    public TypeConstant getIntoVariableType()
        {
        TypeConstant typeInto1 = getUnderlyingType().getIntoVariableType();
        TypeConstant typeInto2 = getUnderlyingType2().getIntoVariableType();

        if (typeInto1 == null)
            {
            return typeInto2;
            }

        if (typeInto2 == null)
            {
            return typeInto1;
            }

        ConstantPool pool    = getConstantPool();
        TypeConstant typeVar = pool.typeVar();
        if (typeInto1.equals(typeVar) || typeInto2.equals(typeVar))
            {
            return typeVar;
            }

        return pool.typeRef();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.IntersectionType;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " | " + m_constType2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return "+".hashCode() ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
