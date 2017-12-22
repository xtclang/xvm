package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.ContributionChain;
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
    protected TypeConstant cloneRelational(TypeConstant type1, TypeConstant type2)
        {
        return getConstantPool().ensureUnionTypeConstant(type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isNullable()
        {
        return m_constType1.isNullable() && m_constType2.isNullable();
        }

    @Override
    public TypeConstant nonNullable()
        {
        return isNullable()
                ? getConstantPool().ensureUnionTypeConstant(m_constType1.nonNullable(),
                                                            m_constType2.nonNullable())
                : this;
        }

    @Override
    public boolean isCongruentWith(TypeConstant that)
        {
        that = that.unwrapForCongruence();
        if (that instanceof UnionTypeConstant)
            {
            TypeConstant      this1 = this.m_constType1;
            TypeConstant      this2 = this.m_constType2;
            UnionTypeConstant thatU = (UnionTypeConstant) that;
            TypeConstant      that1 = thatU.m_constType1;
            TypeConstant      that2 = thatU.m_constType2;
            return     (this1.isCongruentWith(that1) && this2.isCongruentWith(that2))
                    || (this1.isCongruentWith(that2) && this2.isCongruentWith(that1));
            }

        return false;
        }

    @Override
    protected boolean resolveStructure(TypeInfo typeinfo, Access access,
            TypeConstant[] atypeParams, ErrorListener errs)
        {
        return getUnderlyingType() .resolveStructure(typeinfo, access, atypeParams, errs)
            || getUnderlyingType2().resolveStructure(typeinfo, access, atypeParams, errs);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    public List<ContributionChain> collectContributions(
            TypeConstant that, List<TypeConstant> listParams, List<ContributionChain> chains)
        {
        TypeConstant type1 = getUnderlyingType();
        TypeConstant type2 = getUnderlyingType2();

        List<ContributionChain> list1 = type1.collectContributions(that, listParams, new LinkedList<>());
        List<ContributionChain> list2 = type2.collectContributions(that, listParams, new LinkedList<>());

        // any contribution would do
        if (!list1.isEmpty())
            {
            validate(type1, that, list1);
            }

        if (!list2.isEmpty())
            {
            validate(type2, that, list2);
            }

        chains.addAll(list1);
        chains.addAll(list2);

        return chains;
        }

    @Override
    protected List<ContributionChain> collectClassContributions(
            ClassStructure clzThat, List<TypeConstant> listParams, List<ContributionChain> chains)
        {
        TypeConstant type1 = getUnderlyingType();
        TypeConstant type2 = getUnderlyingType2();

        List<ContributionChain> list1 = type1.collectClassContributions(clzThat, listParams, new LinkedList<>());
        List<ContributionChain> list2 = type2.collectClassContributions(clzThat, listParams, new LinkedList<>());

        // both branches have to have contributions
        if (!list1.isEmpty() && !list2.isEmpty())
            {
            chains.addAll(list1);
            chains.addAll(list2);
            }

        return chains;
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant that, Access access, List<TypeConstant> listParams)
        {
        Set<SignatureConstant> setMiss1 = getUnderlyingType().isInterfaceAssignableFrom(that, access, listParams);
        Set<SignatureConstant> setMiss2 = getUnderlyingType2().isInterfaceAssignableFrom(that, access, listParams);

        setMiss1.retainAll(setMiss2); // signatures in both (intersection) are still missing
        return setMiss1;
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
        return m_constType1.getValueString() + " | " + m_constType2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return "|".hashCode() ^ m_constType1.hashCode() ^ m_constType2.hashCode();
        }
    }
