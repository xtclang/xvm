package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
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
    protected TypeConstant cloneRelational(TypeConstant type1, TypeConstant type2)
        {
        return getConstantPool().ensureDifferenceTypeConstant(type1, type2);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public boolean extendsOrImpersonatesClass(IdentityConstant constClass)
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public boolean isClassType()
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public boolean isSingleUnderlyingClass()
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        return false;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass()
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        throw new IllegalStateException();
        }

    @Override
    public Set<IdentityConstant> underlyingClasses()
        {
        // a difference type is NEVER a class type; it always resolves to an interface type
        return Collections.EMPTY_SET;
        }

    @Override
    public boolean isOnlyNullable()
        {
        // difference types are never nullable
        return false;
        }

    @Override
    protected TypeConstant unwrapForCongruence()
        {
        // a difference type is just a duck-type, and has no equality definition of its own -- it
        // uses the equality definition of the root object class itself
        return getConstantPool().typeObject();
        }

    @Override
    protected boolean resolveStructure(TypeInfo typeinfo, Access access,
            TypeConstant[] atypeParams, ErrorListener errs)
        {
        // we've been asked to resolve some type defined as "T1 - T2", which means that we need to
        // first resolve T1 and T2, and then add all the information from T1 that is not in T2 to
        // the passed-in TypeInfo; the primary complication at this point is that we may have
        // type parameter information that will be required to resolve either T1 and/or T2
        // TODO
        return false;

//        Set set1 = m_constType1.getOpMethods(sName, sOp, cParams);
//        Set set2 = m_constType2.getOpMethods(sName, sOp, cParams);
//        if (set1.equals(set2))
//            {
//            return;
//            }
//
//        Set<MethodConstant> setDifference = new HashSet<>(set1);
//        setDifference.removeAll(set2);
//        setOps.addAll(setDifference);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    public List<ContributionChain> collectContributions(
            TypeConstant that, List<TypeConstant> listParams, List<ContributionChain> chains)
        {
        // the difference type is always an interface, so it can only
        // "duck type" to another interface

        if (!that.isClassType())
            {
            chains.add(new ContributionChain(
                new Contribution(Composition.MaybeDuckType, null)));
            }
        return chains;
        }

    @Override
    protected List<ContributionChain> collectClassContributions(
            ClassStructure clzThat, List<TypeConstant> listParams, List<ContributionChain> chains)
        {
        // the difference type is always an interface, so it can only
        // "duck type" to another interface

        chains.add(new ContributionChain(
            new Contribution(Composition.MaybeDuckType, null)));
        return chains;

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
