package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.List;

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


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveTypedefs();
        TypeConstant constResolved2 = constOriginal2.resolveTypedefs();
        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : getConstantPool().ensureIntersectionTypeConstant(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveGenerics(GenericTypeResolver resolver)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveGenerics(resolver);
        TypeConstant constResolved2 = constOriginal2.resolveGenerics(resolver);
        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : getConstantPool().ensureIntersectionTypeConstant(constResolved1, constResolved2);
        }

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
    protected boolean resolveStructure(TypeInfo typeinfo, Access access,
            TypeConstant[] atypeParams, ErrorListener errs)
        {
        // each of the two sub-types needs to be resolved independently, and then only the parts
        // that match should be incorporated into the passed
        return super.resolveStructure(typeinfo, access, atypeParams, errs);

//        Set set1 = m_constType1.getOpMethods(sName, sOp, cParams);
//        Set set2 = m_constType2.getOpMethods(sName, sOp, cParams);
//        if (set1.equals(set2))
//            {
//            setOps.addAll(set1);
//            return;
//            }
//
//        Set<MethodConstant> setIntersection = new HashSet<>(set1);
//        setIntersection.retainAll(set2);
//        setOps.addAll(setIntersection);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected ContributionChain checkAssignableTo(TypeConstant that)
        {
        ContributionChain chain1 = getUnderlyingType().checkAssignableTo(that);
        ContributionChain chain2 = getUnderlyingType2().checkAssignableTo(that);

        boolean fFrom1 = chain1 != null &&
            that.checkAssignableFrom(getUnderlyingType(), chain1);
        boolean fFrom2 = chain2 != null &&
            that.checkAssignableFrom(getUnderlyingType(), chain2);

        if (fFrom1 && fFrom2)
            {
            // TODO: if just one chain is a non-qualified "maybe", convert it into a qualifying one
            // so the caller knows not to check what's already been proven;
            // if both are qualified, merge the qualifiers into a union
            throw new UnsupportedOperationException();
            }

        return null;
        }

    @Override
    protected ContributionChain checkContribution(ClassStructure clzThat)
        {
        ContributionChain chain1 = getUnderlyingType().checkContribution(clzThat);
        ContributionChain chain2 = getUnderlyingType2().checkContribution(clzThat);

        if (chain1 != null || chain2 != null)
            {
            // TODO: merge chains
            if (chain1 == null)
                {
                return chain2;
                }

            if (chain2 == null)
                {
                return chain1;
                }

            throw new UnsupportedOperationException();
            }

        return null;
        }

    @Override
    protected boolean checkAssignableFrom(TypeConstant that, ContributionChain chain)
        {
        // there is nothing that could change the result of "checkAssignableTo"
        return true;
        }

    @Override
    protected boolean isInterfaceAssignableFrom(TypeConstant that, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().isInterfaceAssignableFrom(that, access, listParams)
            && getUnderlyingType2().isInterfaceAssignableFrom(that, access, listParams);
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
