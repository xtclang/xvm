package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;


/**
 * Represent a constant that specifies the union ("|") of two types.
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
                : getConstantPool().ensureUnionTypeConstant(constResolved1, constResolved2);
        }

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
