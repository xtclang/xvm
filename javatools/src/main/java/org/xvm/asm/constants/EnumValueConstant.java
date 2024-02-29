package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.util.PackedInteger;


/**
 * Represent a singleton instance of an enum value class.
 */
public class EnumValueConstant
        extends SingletonConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constClass  the class constant for the singleton value
     */
    public EnumValueConstant(ConstantPool pool, ClassConstant constClass)
        {
        super(pool, Format.EnumValueConst, constClass);

        if (constClass.getComponent().getFormat() != Component.Format.ENUMVALUE)
            {
            throw new IllegalArgumentException("enum value required");
            }
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
    public EnumValueConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return if the class represents an enum value, the rank of this value if the parent's
     *         list of children; -1 otherwise
     */
    public int getPresumedOrdinal()
        {
        ClassStructure clzThis = (ClassStructure) getClassConstant().getComponent();

        int nOrdinal = 0;
        for (Component sibling : clzThis.getParent().children())
            {
            if (sibling.getFormat() == Component.Format.ENUMVALUE)
                {
                if (sibling == clzThis)
                    {
                    return nOrdinal;
                    }
                ++nOrdinal;
                }
            }

        return -1;
        }

    @Override
    public Constant getValue()
        {
        // return a presumed ordinal value as the "value" of the enum
        int iOrdinal = getPresumedOrdinal();
        return iOrdinal < 0
                ? null
                : getConstantPool().ensureIntConstant(iOrdinal);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public EnumValueConstant resolveTypedefs()
        {
        ClassConstant constOld = (ClassConstant) getClassConstant();
        ClassConstant constNew = (ClassConstant) constOld.resolveTypedefs();
        return constNew == constOld
                ? this
                : (EnumValueConstant) getConstantPool().register(
                        new EnumValueConstant(getConstantPool(), constNew));
        }

    @Override
    public PackedInteger getIntValue()
        {
        int iOrdinal = getPresumedOrdinal();
        return iOrdinal >= 0
                ? new PackedInteger(iOrdinal)
                : super.getIntValue();
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        ConstantPool pool = getConstantPool();
        if (that instanceof EnumValueConstant)
            {
            switch (op)
                {
                case I_RANGE_I:
                    return pool.ensureRangeConstant(this, that);

                case E_RANGE_I:
                    return pool.ensureRangeConstant(this, true, that, false);

                case I_RANGE_E:
                    return pool.ensureRangeConstant(this, false, that, true);

                case E_RANGE_E:
                    return pool.ensureRangeConstant(this, true, that, true);

                case SUB:
                    return pool.ensureIntConstant(this.getPresumedOrdinal() -
                        ((EnumValueConstant) that).getPresumedOrdinal());

                case COMP_EQ:
                    return pool.valOf(this.getPresumedOrdinal() ==
                        ((EnumValueConstant) that).getPresumedOrdinal());

                case COMP_NEQ:
                    return pool.valOf(this.getPresumedOrdinal() !=
                        ((EnumValueConstant) that).getPresumedOrdinal());

                case COMP_LT:
                    return pool.valOf(this.getPresumedOrdinal() <
                        ((EnumValueConstant) that).getPresumedOrdinal());

                case COMP_LTEQ:
                    return pool.valOf(this.getPresumedOrdinal() <=
                        ((EnumValueConstant) that).getPresumedOrdinal());

                case COMP_GT:
                    return pool.valOf(this.getPresumedOrdinal() >
                        ((EnumValueConstant) that).getPresumedOrdinal());

                case COMP_GTEQ:
                    return pool.valOf(this.getPresumedOrdinal() >=
                        ((EnumValueConstant) that).getPresumedOrdinal());

                default:
                    break;
                }
            }
        else if (that == null && op == Token.Id.NOT)
            {
            if (getClassConstant().equals(pool.valTrue().getClassConstant()))
                {
                return pool.valFalse();
                }
            if (getClassConstant().equals(pool.valFalse().getClassConstant()))
                {
                return pool.valTrue();
                }
            }

        switch (op)
            {
            case ADD:
                {
                // this can only be "nextValue" operation
                assert that.equals(pool.ensureLiteralConstant(Format.IntLiteral, "1"));

                ClassStructure clzValue = (ClassStructure) getClassConstant().getComponent();
                ClassStructure clzEnum  = (ClassStructure) clzValue.getParent();
                boolean        fNext    = false;
                for (Component child : clzEnum.children())
                    {
                    if (fNext)
                        {
                        if (child instanceof ClassStructure clzChild &&
                                clzChild.getFormat() == Component.Format.ENUMVALUE)
                            {
                            return pool.ensureSingletonConstConstant(clzChild.getIdentityConstant());
                            }
                        continue;
                        }

                    if (child == clzValue)
                        {
                        fNext = true;
                        }
                    }
                return null;
                }

            case SUB:
                {
                // this can only be "prevValue" operation
                assert that.equals(pool.ensureLiteralConstant(Format.IntLiteral, "1"));

                ClassStructure clzValue = (ClassStructure) getClassConstant().getComponent();
                ClassStructure clzEnum  = (ClassStructure) clzValue.getParent();
                ClassStructure clzPrev  = null;
                for (Component child : clzEnum.children())
                    {
                    if (child == clzValue)
                        {
                        return clzPrev == null
                            ? null
                            : pool.ensureSingletonConstConstant(clzPrev.getIdentityConstant());
                        }

                    if (child instanceof ClassStructure clzChild &&
                            clzChild.getFormat() == Component.Format.ENUMVALUE)
                        {
                        clzPrev = clzChild;
                        }
                    }
                return null;
                }
            }

        return super.apply(op, that);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "enum=" + getClassConstant().getName();
        }
    }
