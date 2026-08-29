package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Represent an XVM char string (string of unicode characters) constant.
 */
public final class StringConstant
        extends ValueConstant<String> {
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
    public StringConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);
        m_sVal  = readUtf8String(in);
    }

    /**
     * Construct a constant whose value is a char string.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param sVal  the char string value
     */
    public StringConstant(ConstantPool pool, String sVal) {
        super(pool);

        assert sVal != null;
        m_sVal  = sVal;
    }

    @Override
    protected StringConstant copyForAdoption(AdoptionContext context) {
        // Scalar adoption reconstructs logical value only; the clone helper would
        // silently copy any future owner-local helper field added here.
        return new StringConstant(context.pool(), m_sVal);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Get the value of the constant.
     *
     * @return the constant's char string value as a {@code String}
     */
    public String getValue() {
        return m_sVal;
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.String;
    }

    @Override
    public TypeConstant getType() {
        return getConstantPool().typeString();
    }

    @Override
    public Constant apply(Token.Id op, Constant that) {
        // dispatch on the operator and pattern-match the operand; see CharConstant.apply for
        // the rationale - this replaces dispatch by string concatenation of the operator text
        // and the operand's format name, with a blind cast in every arm
        ConstantPool pool = getConstantPool();
        return switch (op) {
            case ADD -> switch (that) {
                case StringConstant str -> pool.ensureStringConstant(this.m_sVal + str.m_sVal);
                case CharConstant ch -> {
                    assert Character.isValidCodePoint(ch.getValue());
                    yield pool.ensureStringConstant(this.m_sVal + (char) (int) ch.getValue());
                }
                case LiteralConstant lit when lit.getFormat() == Format.IntLiteral
                                           || lit.getFormat() == Format.FPLiteral ->
                        pool.ensureStringConstant(this.m_sVal + lit.getValue());
                case EnumValueConstant enumVal ->
                        pool.ensureStringConstant(
                                this.m_sVal + enumVal.getClassConstant().getName());
                default -> super.apply(op, that);
            };

            case MUL -> switch (that) {
                case LiteralConstant lit when lit.getFormat() == Format.IntLiteral ->
                        repeat(lit.getPackedInteger().getInt());
                case IntConstant n when n.getFormat() == Format.Int64 ->
                        repeat(n.getValue().getInt());
                default -> super.apply(op, that);
            };

            case COMP_EQ   -> that instanceof StringConstant str
                    ? pool.valOf(this.m_sVal.equals(str.m_sVal))         : super.apply(op, that);
            case COMP_NEQ  -> that instanceof StringConstant str
                    ? pool.valOf(!this.m_sVal.equals(str.m_sVal))        : super.apply(op, that);
            case COMP_LT   -> that instanceof StringConstant str
                    ? pool.valOf(this.m_sVal.compareTo(str.m_sVal) < 0)  : super.apply(op, that);
            case COMP_LTEQ -> that instanceof StringConstant str
                    ? pool.valOf(this.m_sVal.compareTo(str.m_sVal) <= 0) : super.apply(op, that);
            case COMP_GT   -> that instanceof StringConstant str
                    ? pool.valOf(this.m_sVal.compareTo(str.m_sVal) > 0)  : super.apply(op, that);
            case COMP_GTEQ -> that instanceof StringConstant str
                    ? pool.valOf(this.m_sVal.compareTo(str.m_sVal) >= 0) : super.apply(op, that);
            case COMP_ORD  -> that instanceof StringConstant str
                    ? pool.valOrd(this.m_sVal.compareTo(str.m_sVal))     : super.apply(op, that);

            case I_RANGE_I -> that instanceof StringConstant
                    ? pool.ensureRangeConstant(this, that)               : super.apply(op, that);

            default -> super.apply(op, that);
        };
    }

    /**
     * @param n  the repetition count
     *
     * @return a string constant repeating this string {@code n} times
     */
    private StringConstant repeat(int n) {
        String s = m_sVal;
        assert n >= 0 && n * s.length() < 1000000;
        return getConstantPool().ensureStringConstant(s.repeat(n));
    }

    @Override
    public Object getLocator() {
        return m_sVal;
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof StringConstant)) {
            return -1;
        }
        return this.m_sVal.compareTo(((StringConstant) that).m_sVal);
    }

    @Override
    public String getValueString() {
        return quotedString(m_sVal);
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writeUtf8String(out, m_sVal);
    }

    @Override
    public String getDescription() {
        return "char-string=" + getValueString();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_sVal);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant character string value.
     */
    private final String m_sVal;
}
