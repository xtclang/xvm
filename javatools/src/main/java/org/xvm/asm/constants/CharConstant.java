package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Hash;
import org.xvm.util.PackedInteger;

import static org.xvm.util.Handy.appendIntAsHex;
import static org.xvm.util.Handy.quotedChar;
import static org.xvm.util.Handy.readUtf8Char;
import static org.xvm.util.Handy.writeUtf8Char;


/**
 * Represent a unicode character constant.
 */
public final class CharConstant
        extends ValueConstant<Integer> {
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
    public CharConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);
        m_chVal = readUtf8Char(in);
    }

    /**
     * Construct a constant whose value is a unicode character.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param chVal  the unicode character value
     */
    public CharConstant(ConstantPool pool, int chVal) {
        super(pool);
        m_chVal = chVal;
    }

    @Override
    protected CharConstant copyForAdoption(AdoptionContext context) {
        // Scalar adoption reconstructs logical value only; the clone helper would
        // silently copy any future owner-local helper field added here.
        return new CharConstant(context.pool(), m_chVal);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's unicode character value as a Java Integer
     */
    @Override
    public Integer getValue() {
        return Integer.valueOf(m_chVal);
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.Char;
    }

    @Override
    public PackedInteger getIntValue() {
        return PackedInteger.valueOf(m_chVal);
    }

    @Override
    public Constant apply(Token.Id op, Constant that) {
        // dispatch on the operator and pattern-match the operand. This replaces a switch over
        // op.TEXT + that.getFormat().name() - dispatch by string concatenation - whose every
        // arm blind-cast the operand to the class its string had just described. The patterns
        // bind the operand once, the compiler checks each binding, and an operand kind that no
        // arm claims falls through to super exactly as the unmatched strings did.
        ConstantPool pool = getConstantPool();
        return switch (op) {
            case ADD -> switch (that) {
                case StringConstant str -> {
                    assert Character.isValidCodePoint(this.m_chVal);
                    yield pool.ensureStringConstant((char) this.m_chVal + str.getValue());
                }
                case CharConstant ch -> {
                    assert Character.isValidCodePoint(this.m_chVal);
                    assert Character.isValidCodePoint(ch.m_chVal);
                    yield pool.ensureStringConstant(
                            String.valueOf((char) this.m_chVal) + (char) ch.m_chVal);
                }
                // "fake" i.e. compile-time only, to support calculations resulting from the
                // use of Range in ForEachStatement
                case LiteralConstant lit when lit.getFormat() == Format.IntLiteral ->
                        pool.ensureCharConstant(m_chVal
                                + lit.toIntConstant(Format.Int32).getIntValue().getInt());
                default -> super.apply(op, that);
            };

            case SUB -> switch (that) {
                case CharConstant ch -> {
                    assert Character.isValidCodePoint(this.m_chVal);
                    assert Character.isValidCodePoint(ch.m_chVal);
                    yield pool.ensureIntConstant((char) this.m_chVal - (char) ch.m_chVal);
                }
                // "fake"; see the ADD arm above
                case LiteralConstant lit when lit.getFormat() == Format.IntLiteral ->
                        pool.ensureCharConstant(m_chVal
                                - lit.toIntConstant(Format.Int32).getIntValue().getInt());
                default -> super.apply(op, that);
            };

            case MUL -> switch (that) {
                case LiteralConstant lit when lit.getFormat() == Format.IntLiteral ->
                        repeat(lit.getPackedInteger().getInt());
                case IntConstant n when n.getFormat() == Format.Int64 ->
                        repeat(n.getValue().getInt());
                default -> super.apply(op, that);
            };

            case COMP_EQ   -> that instanceof CharConstant ch
                    ? pool.valOf(this.m_chVal == ch.m_chVal) : super.apply(op, that);
            case COMP_NEQ  -> that instanceof CharConstant ch
                    ? pool.valOf(this.m_chVal != ch.m_chVal) : super.apply(op, that);
            case COMP_LT   -> that instanceof CharConstant ch
                    ? pool.valOf(this.m_chVal < ch.m_chVal)  : super.apply(op, that);
            case COMP_LTEQ -> that instanceof CharConstant ch
                    ? pool.valOf(this.m_chVal <= ch.m_chVal) : super.apply(op, that);
            case COMP_GT   -> that instanceof CharConstant ch
                    ? pool.valOf(this.m_chVal > ch.m_chVal)  : super.apply(op, that);
            case COMP_GTEQ -> that instanceof CharConstant ch
                    ? pool.valOf(this.m_chVal >= ch.m_chVal) : super.apply(op, that);
            case COMP_ORD  -> that instanceof CharConstant ch
                    ? pool.valOrd(this.m_chVal - ch.m_chVal) : super.apply(op, that);

            case I_RANGE_I -> that instanceof CharConstant
                    ? pool.ensureRangeConstant(this, that)   : super.apply(op, that);

            default -> super.apply(op, that);
        };
    }

    /**
     * @param n  the repetition count
     *
     * @return a string constant repeating this character {@code n} times
     */
    private StringConstant repeat(int n) {
        assert Character.isValidCodePoint(this.m_chVal);
        assert n >= 0 && n < 1000000;

        char[] ach = new char[n];
        Arrays.fill(ach, (char) this.m_chVal);
        return getConstantPool().ensureStringConstant(new String(ach));
    }

    @Override
    public Constant convertTo(TypeConstant typeOut) {
        switch (typeOut.getEcstasyClassName()) {
        case "text.String": {
            int ch = m_chVal;
            if (ch >= Character.MIN_VALUE && ch <= Character.MAX_VALUE) {
                return getConstantPool().
                    ensureStringConstant(Character.valueOf((char) ch).toString());
            }
        }
        }

        return super.convertTo(typeOut);
    }

    @Override
    public TypeConstant getType() {
        return getConstantPool().typeChar();
    }

    @Override
    public Object getLocator() {
        // Integer only guarantees that up to 0x7F is cached
        return m_chVal <= 0x7F ? Character.valueOf((char) m_chVal) : null;
    }

    @Override
    protected int compareDetails(Constant constant) {
        if (!(constant instanceof CharConstant that)) {
            return -1;
        }

        return this.m_chVal - that.m_chVal;
    }

    @Override
    public String getValueString() {
        return m_chVal > 0xFFFF
                ? appendIntAsHex(new StringBuilder("'\\U"), m_chVal).append('\'').toString()
                : quotedChar((char) m_chVal);
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writeUtf8Char(out, m_chVal);
    }

    @Override
    public String getDescription() {
        return "char=" + getValueString() + ", index=" + m_chVal;
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_chVal);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant character code-point value stored as an integer.
     */
    private final int m_chVal;
}
