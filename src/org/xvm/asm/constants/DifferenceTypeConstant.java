package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;


/**
 * Represent a constant that specifies the difference (relative complement) ("-") of two types.
 */
public class DifferenceTypeConstant
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
    public DifferenceTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is the difference of two types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant to union
     * @param constType2  the second TypeConstant to union
     */
    public DifferenceTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType1, constType2);
        }

    @Override
    protected TypeConstant cloneRelational(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return pool.ensureDifferenceTypeConstant(type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isImmutable()
        {
        return m_constType1.isImmutable();
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        // a difference type is NEVER a class type
        return false;
        }

    @Override
    public Category getCategory()
        {
        // a difference type for classes or interfaces is an interface

        Category cat1 = m_constType1.getCategory();
        Category cat2 = m_constType2.getCategory();

        switch (cat1)
            {
            case CLASS:
            case IFACE:
                switch (cat2)
                    {
                    case CLASS:
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
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        throw new IllegalStateException();
        }

    @Override
    public boolean isOnlyNullable()
        {
        // difference types are never nullable
        return false;
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        // only the left side needs to have it and it doesn't matter what the right side has
        return m_constType1.containsGenericParam(sName);
        }

    @Override
    public TypeConstant getGenericParamType(String sName)
        {
        // only the left side needs to have it and it doesn't matter what the right side has
        return m_constType1.getGenericParamType(sName);
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        // for the DifferenceType to contribute a name, the first side needs to find it,
        // but the second should not
        SimpleCollector  collector1 = new SimpleCollector();
        ResolutionResult result1    = m_constType1.resolveContributedName(sName, collector1);

        if (result1 == ResolutionResult.RESOLVED)
            {
            ResolutionResult result2 = m_constType2.resolveContributedName(sName, new SimpleCollector());
            if (result2 == ResolutionResult.RESOLVED)
                {
                return ResolutionResult.UNKNOWN;
                }
            collector.resolvedConstant(collector1.getResolvedConstant());
            }

        return result1;
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        typeActual = typeActual.resolveTypedefs();

        if (getFormat() != typeActual.getFormat())
            {
            return null;
            }

        DifferenceTypeConstant that = (DifferenceTypeConstant) typeActual;

        // only use the first branch
        return this.m_constType1.resolveTypeParameter(that.m_constType1, sFormalName);
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // we've been asked to resolve some type defined as "T1 - T2", which means that we need to
        // first resolve T1 and T2, and then add all the information from T1 that is not in T2 to
        // the passed-in TypeInfo; the primary complication at this point is that we may have
        // type parameter information that will be required to resolve either T1 and/or T2
        TypeInfo info1 = getUnderlyingType().ensureTypeInfo(errs);
        TypeInfo info2 = getUnderlyingType2().ensureTypeInfo(errs);
        // TODO
        throw new UnsupportedOperationException("TODO");
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // this will be answered via duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // this will be answered via duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        // this will be answered via duck-type check
        return Relation.INCOMPATIBLE;
        }

    @Override
    public boolean containsSubstitutableMethod(
            SignatureConstant signature, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, listParams)
            && !getUnderlyingType2().containsSubstitutableMethod(signature, access, listParams);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.DifferenceType;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " - " + m_constType2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return "-".hashCode() ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
