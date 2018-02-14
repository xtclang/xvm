package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.ContributionChain;
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
    protected TypeConstant cloneRelational(TypeConstant type1, TypeConstant type2)
        {
        return getConstantPool().ensureIntersectionTypeConstant(type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isNullable()
        {
        return (m_constType1.isOnlyNullable() ^ m_constType2.isOnlyNullable())
                || m_constType1.isNullable() || m_constType2.isNullable();
        }

    @Override
    public TypeConstant nonNullable()
        {
        if (!isNullable())
            {
            return this;
            }

        if (m_constType1.isOnlyNullable())
            {
            assert !m_constType2.isOnlyNullable();
            return m_constType2.nonNullable();
            }

        if (m_constType2.isOnlyNullable())
            {
            assert !m_constType1.isOnlyNullable();
            return m_constType1.nonNullable();
            }

        return getConstantPool().ensureIntersectionTypeConstant(m_constType1.nonNullable(),
                                                                m_constType2.nonNullable());
        }

    @Override
    public boolean isCongruentWith(TypeConstant that)
        {
        that = that.unwrapForCongruence();
        if (that instanceof IntersectionTypeConstant)
            {
            TypeConstant             this1 = this.m_constType1;
            TypeConstant             this2 = this.m_constType2;
            IntersectionTypeConstant thatI = (IntersectionTypeConstant) that;
            TypeConstant             that1 = thatI.m_constType1;
            TypeConstant             that2 = thatI.m_constType2;
            return     (this1.isCongruentWith(that1) && this2.isCongruentWith(that2))
                    || (this1.isCongruentWith(that2) && this2.isCongruentWith(that1));
            }

        return false;
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // we've been asked to resolve some type defined as "T1 | T2";  first, resolve T1 and T2
        TypeInfo info1 = getUnderlyingType().ensureTypeInfo(errs);
        TypeInfo info2 = getUnderlyingType2().ensureTypeInfo(errs);
        // TODO CP
        return info1;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    public List<ContributionChain> collectContributions(
            TypeConstant thatLeft, List<TypeConstant> listRight, List<ContributionChain> chains)
        {
        assert listRight.isEmpty();

        TypeConstant thisRight1 = getUnderlyingType();
        TypeConstant thisRight2 = getUnderlyingType2();

        List<ContributionChain> chains1 = thisRight1.collectContributions(thatLeft, listRight, new ArrayList<>());
        List<ContributionChain> chains2 = thisRight2.collectContributions(thatLeft, new ArrayList<>(), new ArrayList<>());

        // both branches need to contribute
        if (!chains1.isEmpty() && !chains2.isEmpty())
            {
            validate(thisRight1, thatLeft, chains1);
            validate(thisRight2, thatLeft, chains2);

            if (!chains1.isEmpty() && !chains2.isEmpty())
                {
                chains.addAll(chains1);
                chains.addAll(chains2);
                }
            }

        return chains;
        }

    @Override
    protected List<ContributionChain> collectClassContributions(
            ClassStructure clzRight, List<TypeConstant> listRight, List<ContributionChain> chains)
        {
        TypeConstant thisLeft1 = getUnderlyingType();
        TypeConstant thisLeft2 = getUnderlyingType2();

        List<ContributionChain> chains1 = thisLeft1.collectClassContributions(clzRight, listRight, new ArrayList<>());
        List<ContributionChain> chains2 = thisLeft2.collectClassContributions(clzRight, listRight, new ArrayList<>());

        // any contribution would do
        chains.addAll(chains1);
        chains.addAll(chains2);

        return chains;
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(
            TypeConstant thatRight, Access accessLeft, List<TypeConstant> listLeft)
        {
        Set<SignatureConstant> setMiss1 = getUnderlyingType().isInterfaceAssignableFrom(thatRight, accessLeft, listLeft);
        Set<SignatureConstant> setMiss2 = getUnderlyingType2().isInterfaceAssignableFrom(thatRight, accessLeft, listLeft);

        if (setMiss1.isEmpty())
            {
            return setMiss1; // type1 is assignable from that
            }
        if (setMiss2.isEmpty())
            {
            return setMiss2; // type2 is assignable from that
            }

        // neither is assignable; merge the misses
        setMiss1.addAll(setMiss2);

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
    public boolean isIntoMethodType()
        {
        return getUnderlyingType().isIntoMethodType()
            || getUnderlyingType2().isIntoMethodType();
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
