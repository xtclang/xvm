package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigDecimal;
import java.math.MathContext;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.type.Decimal64;

import org.xvm.util.Hash;
import org.xvm.util.PackedInteger;


/**
 * Represent an up to 128-bit signed integer constant, and a bunch of lesser-known formats of
 * integer constants as well, but NOT the 8-bit integer constants.
 *
 * @see ByteConstant
 */
public class IntConstant
        extends ValueConstant {
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
    public IntConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        this(pool, format, new PackedInteger(in));
    }

    /**
     * Construct a constant whose value is a PackedInteger.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the Constant Format
     * @param pint    the PackedInteger value
     */
    public IntConstant(ConstantPool pool, Format format, PackedInteger pint) {
        super(pool);

        if (format == null) {
            throw new IllegalStateException("format required");
        }
        if (pint == null) {
            throw new IllegalStateException("integer value required");
        }
        pint.verifyInitialized();

        int     cBytes;
        boolean fUnsigned;
        switch (format) {
        case Int16:
            cBytes    = 2;
            fUnsigned = false;
            break;
        case Int32:
            cBytes    = 4;
            fUnsigned = false;
            break;
        case Int64:
            cBytes    = 8;
            fUnsigned = false;
            break;
        case Int128:
            cBytes    = 16;
            fUnsigned = false;
            break;
        case IntN:
            // some arbitrary limit that only exists in the Java implementation
            cBytes    = 1024;
            fUnsigned = false;
            break;

        case UInt16:
            cBytes    = 2;
            fUnsigned = true;
            break;
        case UInt32:
            cBytes    = 4;
            fUnsigned = true;
            break;
        case UInt64:
            cBytes    = 8;
            fUnsigned = true;
            break;
        case UInt128:
            cBytes    = 16;
            fUnsigned = true;
            break;
        case UIntN:
            // some arbitrary limit that only exists in the Java implementation
            cBytes    = 1024;
            fUnsigned = true;
            break;

        default:
            throw new IllegalStateException("unsupported format: " + format);
        }

        if (fUnsigned) {
            if (pint.compareTo(PackedInteger.ZERO) < 0) {
                throw new IllegalStateException("illegal unsigned value: " + pint);
            }
            if (pint.getUnsignedByteSize() > cBytes
                    || format == Format.UInt64 && pint.compareTo(PackedInteger.SINT16_MAX) > 0) {
                throw new IllegalStateException("value exceeds " + cBytes + " bytes: " + pint);
            }
        } else if (pint.getSignedByteSize() > cBytes) {
            throw new IllegalStateException("value exceeds " + cBytes + " bytes: " + pint);
        }

        m_fmt  = format;
        m_pint = pint;
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a PackedInteger
     */
    @Override
    public PackedInteger getValue() {
        return m_pint;
    }

    /**
     * @return true iff the format is an unsigned integer format
     */
    public boolean isUnsigned() {
        return switch (m_fmt) {
            case Int16, Int32, Int64, Int128, IntN -> false;
            case UInt16, UInt32, UInt64, UInt128, UIntN -> true;
            default -> throw new IllegalStateException();
        };
    }

    /**
     * @return the number of bytes in the integer format
     */
    public int byteSize() {
        return switch (m_fmt) {
            case Int16, UInt16 -> 2;
            case Int32, UInt32 -> 4;
            case Int64, UInt64 -> 8;
            case Int128, UInt128 -> 16;
            default -> throw new IllegalStateException();
        };
    }

    /**
     * @return the minimum value for the format of this IntConstant
     */
    public PackedInteger getMinLimit() {
        return getMinLimit(m_fmt);
    }

    /**
     * @param format  the format to determine the minimum value of
     *
     * @return the minimum value for the specified format of IntConstant
     */
    public static PackedInteger getMinLimit(Format format) {
        return switch (format) {
            case Int16  -> PackedInteger.SINT2_MIN;
            case Int32  -> PackedInteger.SINT4_MIN;
            case Int64  -> PackedInteger.SINT8_MIN;
            case Int128 -> PackedInteger.SINT16_MIN;
            case IntN   -> PackedInteger.SINTN_MIN;     // note: just an arbitrary limit; no such limit in Ecstasy
            case UInt16, UInt32, UInt64, UInt128, UIntN -> PackedInteger.ZERO;
            default -> throw new IllegalStateException();
        };
    }

    /**
     * @return the maximum value for the format of this IntConstant
     */
    public PackedInteger getMaxLimit() {
        return getMaxLimit(m_fmt);
    }

    /**
     * @param format  the format to determine the maximum value of
     *
     * @return the maximum value for the specified format of IntConstant
     */
    public static PackedInteger getMaxLimit(Format format) {
        return switch (format) {
            case Int16   -> PackedInteger.SINT2_MAX;
            case Int32   -> PackedInteger.SINT4_MAX;
            case Int64   -> PackedInteger.SINT8_MAX;
            case Int128  -> PackedInteger.SINT16_MAX;
            case IntN    -> PackedInteger.SINTN_MAX;    // note: just an arbitrary limit; no such limit in Ecstasy
            case UInt16  -> PackedInteger.UINT2_MAX;
            case UInt32  -> PackedInteger.UINT4_MAX;
            case UInt64  -> PackedInteger.UINT8_MAX;
            case UInt128 -> PackedInteger.UINT16_MAX;
            case UIntN   -> PackedInteger.UINTN_MAX;    // note: just an arbitrary limit; no such limit in Ecstasy
            default -> throw new IllegalStateException();
        };
    }

    /**
     * Add another IntConstant to the value of this IntConstant.
     *
     * @param that  an IntConstant that must be of the same format as this
     *
     * @return the sum, as an IntConstant of the same format as this
     *
     * @throws IllegalStateException  if the formats do not match
     * @throws ArithmeticException    on overflow
     */
    public IntConstant add(IntConstant that) {
        if (this.getFormat() != that.getFormat()) {
            throw new IllegalStateException("format mismatch: this=" + this.getFormat()
                    + ", that=" + that.getFormat());
        }

        return add(that.getValue());
    }

    /**
     *
     * @param that
     * @return
     *
     * @throws IllegalStateException  if the passed literal is not an integer literal
     * @throws ArithmeticException    on overflow
     */
    public IntConstant add(LiteralConstant that) {
        if (that.getFormat() != Format.IntLiteral) {
            throw new IllegalStateException();
        }

        PackedInteger piThat = that.getPackedInteger();
        if (piThat.compareTo(getMinLimit()) < 0 || piThat.compareTo(getMaxLimit()) > 0) {
            throw new ArithmeticException("value to add is out of range");
        }

        return add(piThat);
    }

    /**
     * Add a PackedInteger to the value of this IntConstant.
     *
     * @param piThat  a PackedInteger
     *
     * @return the sum, as an IntConstant of the same format as this
     *
     * @throws ArithmeticException  on overflow
     */
    protected IntConstant add(PackedInteger piThat) {
        PackedInteger piVal = this.getValue().add(piThat);
        if (piVal.compareTo(getMinLimit()) < 0 || piVal.compareTo(getMaxLimit()) > 0) {
            throw new ArithmeticException("overflow");
        }

        return getConstantPool().ensureIntConstant(piVal, getFormat());
    }

    /**
     * Create a new IntConstant with the specified value, but only if it is in the legal range of
     * this IntConstant.
     *
     * @param n  a PackedInteger value
     *
     * @return the corresponding Int8Constant
     *
     * @throws ArithmeticException  if the value is out of range
     */
    public IntConstant validate(PackedInteger n) {
        if (n.compareTo(getMinLimit()) < 0 || n.compareTo(getMaxLimit()) > 0) {
            n = n.and(switch (byteSize()) {
                case 2  -> PackedInteger.UINT2_MAX;
                case 4  -> PackedInteger.UINT4_MAX;
                case 8  -> PackedInteger.UINT8_MAX;
                case 16 -> PackedInteger.UINT16_MAX;
                default -> throw new IllegalStateException("Unexpected byte size: " + byteSize());
            });
        }

        return getConstantPool().ensureIntConstant(n, getFormat());
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return m_fmt;
    }

    @Override
    public PackedInteger getIntValue() {
        return m_pint;
    }

    @Override
    public Constant apply(Token.Id op, Constant that) {
        switch (that == null
                    ?                           op.TEXT + this.getFormat().name()
                    : this.getFormat().name() + op.TEXT + that.getFormat().name()) {
        // TODO .is() / .as()

        case "+Int":
        case "+UInt":
        case "+Int16":
        case "+Int32":
        case "+Int64":
        case "+Int128":
        case "+IntN":
        case "+UInt16":
        case "+UInt32":
        case "+UInt64":
        case "+UInt128":
        case "+UIntN":
            return this;

        case "-Int":
        case "-UInt":
        case "-Int16":
        case "-Int32":
        case "-Int64":
        case "-Int128":
        case "-IntN":
        case "-UInt16":
        case "-UInt32":
        case "-UInt64":
        case "-UInt128":
        case "-UIntN":
            return validate(this.getValue().negate());

        case "~Int":
        case "~Int16":
        case "~Int32":
        case "~Int64":
        case "~Int128":
        case "~IntN":
        case "~UInt":
        case "~UInt16":
        case "~UInt32":
        case "~UInt64":
        case "~UInt128":
        case "~UIntN":
            return validate(this.getValue().complement());

        case "Int+IntLiteral":
        case "Int-IntLiteral":
        case "Int*IntLiteral":
        case "Int/IntLiteral":
        case "Int%IntLiteral":
        case "Int..IntLiteral":
        case "Int..<IntLiteral":
        case "Int==IntLiteral":
        case "Int!=IntLiteral":
        case "Int<IntLiteral":
        case "Int<=IntLiteral":
        case "Int>IntLiteral":
        case "Int>=IntLiteral":
        case "Int<=>IntLiteral":

        case "UInt+IntLiteral":
        case "UInt-IntLiteral":
        case "UInt*IntLiteral":
        case "UInt/IntLiteral":
        case "UInt%IntLiteral":
        case "UInt..IntLiteral":
        case "UInt..<IntLiteral":
        case "UInt==IntLiteral":
        case "UInt!=IntLiteral":
        case "UInt<IntLiteral":
        case "UInt<=IntLiteral":
        case "UInt>IntLiteral":
        case "UInt>=IntLiteral":
        case "UInt<=>IntLiteral":

        case "Int16+IntLiteral":
        case "Int16-IntLiteral":
        case "Int16*IntLiteral":
        case "Int16/IntLiteral":
        case "Int16%IntLiteral":
        case "Int16&IntLiteral":
        case "Int16|IntLiteral":
        case "Int16^IntLiteral":
        case "Int16..IntLiteral":
        case "Int16..<IntLiteral":
        case "Int16==IntLiteral":
        case "Int16!=IntLiteral":
        case "Int16<IntLiteral":
        case "Int16<=IntLiteral":
        case "Int16>IntLiteral":
        case "Int16>=IntLiteral":
        case "Int16<=>IntLiteral":
        case "Int32+IntLiteral":
        case "Int32-IntLiteral":
        case "Int32*IntLiteral":
        case "Int32/IntLiteral":
        case "Int32%IntLiteral":
        case "Int32&IntLiteral":
        case "Int32|IntLiteral":
        case "Int32^IntLiteral":
        case "Int32..IntLiteral":
        case "Int32..<IntLiteral":
        case "Int32==IntLiteral":
        case "Int32!=IntLiteral":
        case "Int32<IntLiteral":
        case "Int32<=IntLiteral":
        case "Int32>IntLiteral":
        case "Int32>=IntLiteral":
        case "Int32<=>IntLiteral":
        case "Int64+IntLiteral":
        case "Int64-IntLiteral":
        case "Int64*IntLiteral":
        case "Int64/IntLiteral":
        case "Int64%IntLiteral":
        case "Int64&IntLiteral":
        case "Int64|IntLiteral":
        case "Int64^IntLiteral":
        case "Int64..IntLiteral":
        case "Int64..<IntLiteral":
        case "Int64==IntLiteral":
        case "Int64!=IntLiteral":
        case "Int64<IntLiteral":
        case "Int64<=IntLiteral":
        case "Int64>IntLiteral":
        case "Int64>=IntLiteral":
        case "Int64<=>IntLiteral":
        case "Int128+IntLiteral":
        case "Int128-IntLiteral":
        case "Int128*IntLiteral":
        case "Int128/IntLiteral":
        case "Int128%IntLiteral":
        case "Int128&IntLiteral":
        case "Int128|IntLiteral":
        case "Int128^IntLiteral":
        case "Int128..IntLiteral":
        case "Int128..<IntLiteral":
        case "Int128==IntLiteral":
        case "Int128!=IntLiteral":
        case "Int128<IntLiteral":
        case "Int128<=IntLiteral":
        case "Int128>IntLiteral":
        case "Int128>=IntLiteral":
        case "Int128<=>IntLiteral":
        case "IntN+IntLiteral":
        case "IntN-IntLiteral":
        case "IntN*IntLiteral":
        case "IntN/IntLiteral":
        case "IntN%IntLiteral":
        case "IntN&IntLiteral":
        case "IntN|IntLiteral":
        case "IntN^IntLiteral":
        case "IntN..IntLiteral":
        case "IntN..<IntLiteral":
        case "IntN==IntLiteral":
        case "IntN!=IntLiteral":
        case "IntN<IntLiteral":
        case "IntN<=IntLiteral":
        case "IntN>IntLiteral":
        case "IntN>=IntLiteral":
        case "IntN<=>IntLiteral":
        case "UInt16+IntLiteral":
        case "UInt16-IntLiteral":
        case "UInt16*IntLiteral":
        case "UInt16/IntLiteral":
        case "UInt16%IntLiteral":
        case "UInt16&IntLiteral":
        case "UInt16|IntLiteral":
        case "UInt16^IntLiteral":
        case "UInt16..IntLiteral":
        case "UInt16..<IntLiteral":
        case "UInt16==IntLiteral":
        case "UInt16!=IntLiteral":
        case "UInt16<IntLiteral":
        case "UInt16<=IntLiteral":
        case "UInt16>IntLiteral":
        case "UInt16>=IntLiteral":
        case "UInt16<=>IntLiteral":
        case "UInt32+IntLiteral":
        case "UInt32-IntLiteral":
        case "UInt32*IntLiteral":
        case "UInt32/IntLiteral":
        case "UInt32%IntLiteral":
        case "UInt32&IntLiteral":
        case "UInt32|IntLiteral":
        case "UInt32^IntLiteral":
        case "UInt32..IntLiteral":
        case "UInt32..<IntLiteral":
        case "UInt32==IntLiteral":
        case "UInt32!=IntLiteral":
        case "UInt32<IntLiteral":
        case "UInt32<=IntLiteral":
        case "UInt32>IntLiteral":
        case "UInt32>=IntLiteral":
        case "UInt32<=>IntLiteral":
        case "UInt64+IntLiteral":
        case "UInt64-IntLiteral":
        case "UInt64*IntLiteral":
        case "UInt64/IntLiteral":
        case "UInt64%IntLiteral":
        case "UInt64&IntLiteral":
        case "UInt64|IntLiteral":
        case "UInt64^IntLiteral":
        case "UInt64..IntLiteral":
        case "UInt64..<IntLiteral":
        case "UInt64==IntLiteral":
        case "UInt64!=IntLiteral":
        case "UInt64<IntLiteral":
        case "UInt64<=IntLiteral":
        case "UInt64>IntLiteral":
        case "UInt64>=IntLiteral":
        case "UInt64<=>IntLiteral":
        case "UInt128+IntLiteral":
        case "UInt128-IntLiteral":
        case "UInt128*IntLiteral":
        case "UInt128/IntLiteral":
        case "UInt128%IntLiteral":
        case "UInt128&IntLiteral":
        case "UInt128|IntLiteral":
        case "UInt128^IntLiteral":
        case "UInt128..IntLiteral":
        case "UInt128..<IntLiteral":
        case "UInt128==IntLiteral":
        case "UInt128!=IntLiteral":
        case "UInt128<IntLiteral":
        case "UInt128<=IntLiteral":
        case "UInt128>IntLiteral":
        case "UInt128>=IntLiteral":
        case "UInt128<=>IntLiteral":
        case "UIntN+IntLiteral":
        case "UIntN-IntLiteral":
        case "UIntN*IntLiteral":
        case "UIntN/IntLiteral":
        case "UIntN%IntLiteral":
        case "UIntN&IntLiteral":
        case "UIntN|IntLiteral":
        case "UIntN^IntLiteral":
        case "UIntN..IntLiteral":
        case "UIntN..<IntLiteral":
        case "UIntN==IntLiteral":
        case "UIntN!=IntLiteral":
        case "UIntN<IntLiteral":
        case "UIntN<=IntLiteral":
        case "UIntN>IntLiteral":
        case "UIntN>=IntLiteral":
        case "UIntN<=>IntLiteral":
            return apply(op, ((LiteralConstant) that).toIntConstant(getFormat()));

        case "Int16<<IntLiteral":
        case "Int16>>IntLiteral":
        case "Int16>>>IntLiteral":
        case "Int32<<IntLiteral":
        case "Int32>>IntLiteral":
        case "Int32>>>IntLiteral":
        case "Int64<<IntLiteral":
        case "Int64>>IntLiteral":
        case "Int64>>>IntLiteral":
        case "Int128<<IntLiteral":
        case "Int128>>IntLiteral":
        case "Int128>>>IntLiteral":
        case "IntN<<IntLiteral":
        case "IntN>>IntLiteral":
        case "IntN>>>IntLiteral":
        case "UInt16<<IntLiteral":
        case "UInt16>>IntLiteral":
        case "UInt16>>>IntLiteral":
        case "UInt32<<IntLiteral":
        case "UInt32>>IntLiteral":
        case "UInt32>>>IntLiteral":
        case "UInt64<<IntLiteral":
        case "UInt64>>IntLiteral":
        case "UInt64>>>IntLiteral":
        case "UInt128<<IntLiteral":
        case "UInt128>>IntLiteral":
        case "UInt128>>>IntLiteral":
        case "UIntN<<IntLiteral":
        case "UIntN>>IntLiteral":
        case "UIntN>>>IntLiteral":
            return apply(op, ((LiteralConstant) that).toIntConstant(Format.Int64));

        case "Int+Int":
        case "UInt+UInt":
        case "Int16+Int16":
        case "Int32+Int32":
        case "Int64+Int64":
        case "Int128+Int128":
        case "IntN+IntN":
        case "UInt16+UInt16":
        case "UInt32+UInt32":
        case "UInt64+UInt64":
        case "UInt128+UInt128":
        case "UIntN+UIntN":
            return validate(this.getValue().add(((IntConstant) that).getValue()));

        case "Int-Int":
        case "UInt-UInt":
        case "Int16-Int16":
        case "Int32-Int32":
        case "Int64-Int64":
        case "Int128-Int128":
        case "IntN-IntN":
        case "UInt16-UInt16":
        case "UInt32-UInt32":
        case "UInt64-UInt64":
        case "UInt128-UInt128":
        case "UIntN-UIntN":
            return validate(this.getValue().sub(((IntConstant) that).getValue()));

        case "Int*Int":
        case "UInt*UInt":
        case "Int16*Int16":
        case "Int32*Int32":
        case "Int64*Int64":
        case "Int128*Int128":
        case "IntN*IntN":
        case "UInt16*UInt16":
        case "UInt32*UInt32":
        case "UInt64*UInt64":
        case "UInt128*UInt128":
        case "UIntN*UIntN":
            return validate(this.getValue().mul(((IntConstant) that).getValue()));

        case "Int/Int":
        case "UInt/UInt":
        case "Int16/Int16":
        case "Int32/Int32":
        case "Int64/Int64":
        case "Int128/Int128":
        case "IntN/IntN":
        case "UInt16/UInt16":
        case "UInt32/UInt32":
        case "UInt64/UInt64":
        case "UInt128/UInt128":
        case "UIntN/UIntN":
            return validate(this.getValue().div(((IntConstant) that).getValue()));

        case "Int%Int":
        case "UInt%UInt":
        case "Int16%Int16":
        case "Int32%Int32":
        case "Int64%Int64":
        case "Int128%Int128":
        case "IntN%IntN":
        case "UInt16%UInt16":
        case "UInt32%UInt32":
        case "UInt64%UInt64":
        case "UInt128%UInt128":
        case "UIntN%UIntN":
            return validate(this.getValue().mod(((IntConstant) that).getValue()));

        case "Int16&Int16":
        case "Int32&Int32":
        case "Int64&Int64":
        case "Int128&Int128":
        case "IntN&IntN":
        case "UInt16&UInt16":
        case "UInt32&UInt32":
        case "UInt64&UInt64":
        case "UInt128&UInt128":
        case "UIntN&UIntN":
            return validate(this.getValue().and(((IntConstant) that).getValue()));

        case "Int16|Int16":
        case "Int32|Int32":
        case "Int64|Int64":
        case "Int128|Int128":
        case "IntN|IntN":
        case "UInt16|UInt16":
        case "UInt32|UInt32":
        case "UInt64|UInt64":
        case "UInt128|UInt128":
        case "UIntN|UIntN":
            return validate(this.getValue().or(((IntConstant) that).getValue()));

        case "Int16^Int16":
        case "Int32^Int32":
        case "Int64^Int64":
        case "Int128^Int128":
        case "IntN^IntN":
        case "UInt16^UInt16":
        case "UInt32^UInt32":
        case "UInt64^UInt64":
        case "UInt128^UInt128":
        case "UIntN^UIntN":
            return validate(this.getValue().xor(((IntConstant) that).getValue()));

        case "Int..Int":
        case "UInt..UInt":
        case "Int16..Int16":
        case "Int32..Int32":
        case "Int64..Int64":
        case "Int128..Int128":
        case "IntN..IntN":
        case "UInt16..UInt16":
        case "UInt32..UInt32":
        case "UInt64..UInt64":
        case "UInt128..UInt128":
        case "UIntN..UIntN":
            return ConstantPool.getCurrentPool().ensureRangeConstant(this, that);

        case "Int..<Int":
        case "UInt..<UInt":
        case "Int16..<Int16":
        case "Int32..<Int32":
        case "Int64..<Int64":
        case "Int128..<Int128":
        case "IntN..<IntN":
        case "UInt16..<UInt16":
        case "UInt32..<UInt32":
        case "UInt64..<UInt64":
        case "UInt128..<UInt128":
        case "UIntN..<UIntN":
            return ConstantPool.getCurrentPool().ensureRangeConstant(this, false, that, true);

        case "Int>..Int":
        case "UInt>..UInt":
        case "Int16>..Int16":
        case "Int32>..Int32":
        case "Int64>..Int64":
        case "Int128>..Int128":
        case "IntN>..IntN":
        case "UInt16>..UInt16":
        case "UInt32>..UInt32":
        case "UInt64>..UInt64":
        case "UInt128>..UInt128":
        case "UIntN>..UIntN":
            return ConstantPool.getCurrentPool().ensureRangeConstant(this, true, that, false);

        case "Int>..<Int":
        case "UInt>..<UInt":
        case "Int16>..<Int16":
        case "Int32>..<Int32":
        case "Int64>..<Int64":
        case "Int128>..<Int128":
        case "IntN>..<IntN":
        case "UInt16>..<UInt16":
        case "UInt32>..<UInt32":
        case "UInt64>..<UInt64":
        case "UInt128>..<UInt128":
        case "UIntN>..<UIntN":
            return ConstantPool.getCurrentPool().ensureRangeConstant(this, true, that, true);

        case "Int<<Int":
        case "Int16<<Int64":
        case "Int32<<Int64":
        case "Int64<<Int64":
        case "Int128<<Int64":
        case "IntN<<Int64":
        case "UInt16<<Int64":
        case "UInt32<<Int64":
        case "UInt64<<Int64":
        case "UInt128<<Int64":
        case "UIntN<<Int64":
        case "Int16<<Int":
        case "Int32<<Int":
        case "Int64<<Int":
        case "Int128<<Int":
        case "IntN<<Int":
        case "UInt<<Int":
        case "UInt16<<Int":
        case "UInt32<<Int":
        case "UInt64<<Int":
        case "UInt128<<Int":
        case "UIntN<<Int":
            return validate(this.getValue().shl(((IntConstant) that).getValue()));

        case "Int>>Int":
        case "Int16>>Int64":
        case "Int32>>Int64":
        case "Int64>>Int64":
        case "Int128>>Int64":
        case "IntN>>Int64":
        case "UInt16>>Int64":
        case "UInt32>>Int64":
        case "UInt64>>Int64":
        case "UInt128>>Int64":
        case "UIntN>>Int64":
        case "Int16>>Int":
        case "Int32>>Int":
        case "Int64>>Int":
        case "Int128>>Int":
        case "IntN>>Int":
        case "UInt>>Int":
        case "UInt16>>Int":
        case "UInt32>>Int":
        case "UInt64>>Int":
        case "UInt128>>Int":
        case "UIntN>>Int":
            return validate(this.getValue().shr(((IntConstant) that).getValue()));

        case "Int16>>>Int64":
        case "Int32>>>Int64":
        case "Int64>>>Int64":
        case "Int128>>>Int64":
        case "IntN>>>Int64":
        case "UInt16>>>Int64":
        case "UInt32>>>Int64":
        case "UInt64>>>Int64":
        case "UInt128>>>Int64":
        case "UIntN>>>Int64":
        case "Int16>>>Int":
        case "Int32>>>Int":
        case "Int64>>>Int":
        case "Int128>>>Int":
        case "IntN>>>Int":
        case "UInt16>>>Int":
        case "UInt32>>>Int":
        case "UInt64>>>Int":
        case "UInt128>>>Int":
        case "UIntN>>>Int":
            return validate(this.getValue().ushr(((IntConstant) that).getValue()));

        case "Int==Int":
        case "UInt==UInt":
        case "Int16==Int16":
        case "Int32==Int32":
        case "Int64==Int64":
        case "Int128==Int128":
        case "IntN==IntN":
        case "UInt16==UInt16":
        case "UInt32==UInt32":
        case "UInt64==UInt64":
        case "UInt128==UInt128":
        case "UIntN==UIntN":

        case "Int!=Int":
        case "UInt!=UInt":
        case "Int16!=Int16":
        case "Int32!=Int32":
        case "Int64!=Int64":
        case "Int128!=Int128":
        case "IntN!=IntN":
        case "UInt16!=UInt16":
        case "UInt32!=UInt32":
        case "UInt64!=UInt64":
        case "UInt128!=UInt128":
        case "UIntN!=UIntN":

        case "Int<Int":
        case "UInt<UInt":
        case "Int16<Int16":
        case "Int32<Int32":
        case "Int64<Int64":
        case "Int128<Int128":
        case "IntN<IntN":
        case "UInt16<UInt16":
        case "UInt32<UInt32":
        case "UInt64<UInt64":
        case "UInt128<UInt128":
        case "UIntN<UIntN":

        case "Int<=Int":
        case "UInt<=UInt":
        case "Int16<=Int16":
        case "Int32<=Int32":
        case "Int64<=Int64":
        case "Int128<=Int128":
        case "IntN<=IntN":
        case "UInt16<=UInt16":
        case "UInt32<=UInt32":
        case "UInt64<=UInt64":
        case "UInt128<=UInt128":
        case "UIntN<=UIntN":

        case "Int>Int":
        case "UInt>UInt":
        case "Int16>Int16":
        case "Int32>Int32":
        case "Int64>Int64":
        case "Int128>Int128":
        case "IntN>IntN":
        case "UInt16>UInt16":
        case "UInt32>UInt32":
        case "UInt64>UInt64":
        case "UInt128>UInt128":
        case "UIntN>UIntN":

        case "Int>=Int":
        case "UInt>=UInt":
        case "Int16>=Int16":
        case "Int32>=Int32":
        case "Int64>=Int64":
        case "Int128>=Int128":
        case "IntN>=IntN":
        case "UInt16>=UInt16":
        case "UInt32>=UInt32":
        case "UInt64>=UInt64":
        case "UInt128>=UInt128":
        case "UIntN>=UIntN":

        case "Int<=>Int":
        case "UInt<=>UInt":
        case "Int16<=>Int16":
        case "Int32<=>Int32":
        case "Int64<=>Int64":
        case "Int128<=>Int128":
        case "IntN<=>IntN":
        case "UInt16<=>UInt16":
        case "UInt32<=>UInt32":
        case "UInt64<=>UInt64":
        case "UInt128<=>UInt128":
        case "UIntN<=>UIntN":
            return translateOrder(this.getValue().cmp(((IntConstant) that).getValue()), op);
        }

        return super.apply(op, that);
    }

    @Override
    public Constant convertTo(TypeConstant typeOut) {
        Constant constant = super.convertTo(typeOut);
        if (constant != null) {
            return constant;
        }

        ConstantPool pool = getConstantPool();
        if (typeOut.equals(pool.typeInt8())) {
            return toByteConstant(Format.Int8);
        }
        if (typeOut.equals(pool.typeInt16())) {
            return toIntConstant(Format.Int16);
        }
        if (typeOut.equals(pool.typeInt32())) {
            return toIntConstant(Format.Int32);
        }
        if (typeOut.equals(pool.typeInt64())) {
            return toIntConstant(Format.Int64);
        }
        if (typeOut.equals(pool.typeInt128())) {
            return toIntConstant(Format.Int128);
        }
        if (typeOut.equals(pool.typeIntN())) {
            return toIntConstant(Format.IntN);
        }
        if (typeOut.equals(pool.typeBit())) {
            return toByteConstant(Format.Bit);
        }
        if (typeOut.equals(pool.typeNibble())) {
            return toByteConstant(Format.Nibble);
        }
        if (typeOut.equals(pool.typeUInt8())) {
            return toByteConstant(Format.UInt8);
        }
        if (typeOut.equals(pool.typeUInt16())) {
            return toIntConstant(Format.UInt16);
        }
        if (typeOut.equals(pool.typeUInt32())) {
            return toIntConstant(Format.UInt32);
        }
        if (typeOut.equals(pool.typeUInt64())) {
            return toIntConstant(Format.UInt64);
        }
        if (typeOut.equals(pool.typeUInt128())) {
            return toIntConstant(Format.UInt128);
        }
        if (typeOut.equals(pool.typeUIntN())) {
            return toIntConstant(Format.UIntN);
        }
        if (typeOut.equals(pool.typeDec64())) {
            return toDecConstant(Format.Dec64);
        }
        if (typeOut.equals(pool.typeFloat32())) {
            return toFloatConstant(Format.Float32);
        }
        if (typeOut.equals(pool.typeFloat64())) {
            return toFloat64Constant();
        }
        return null;
    }

    /**
     * Convert this IntConstant to a ByteConstant of the specified format.
     *
     * @param format  the format of the ByteConstant to use
     *
     * @return a ByteConstant
     *
     * @throws ArithmeticException  on overflow
     */
    public ByteConstant toByteConstant(Format format) {
        PackedInteger pi = getValue();
        if (       pi.compareTo(PackedInteger.valueOf(ByteConstant.getMinLimit(format))) < 0
                || pi.compareTo(PackedInteger.valueOf(ByteConstant.getMaxLimit(format))) > 0) {
            throw new ArithmeticException("out of range: " + pi);
        }

        return getConstantPool().ensureByteConstant(format, pi.getInt());
    }

    /**
     * Convert this IntConstant to an IntConstant of the specified format.
     *
     * @param format  the format of the IntConstant to use
     *
     * @return an IntConstant
     *
     * @throws ArithmeticException  on overflow
     */
    public IntConstant toIntConstant(Format format) {
        return toIntConstant(format, getValue(), getConstantPool());
    }

    /**
     * Convert this IntConstant to a DecimalConstant of the specified format.
     *
     * @param format  the format of the DecimalConstant to use
     *
     * @return a DecimalConstant
     */
    public DecimalConstant toDecConstant(Format format) {
        return toDecConstant(format, getValue(), getConstantPool());
    }

    /**
     * Convert this IntConstant to a FloatConstant of the specified format.
     *
     * @param format  the format of the DecimalConstant to use
     *
     * @return a DecimalConstant
     */
    public FloatConstant toFloatConstant(Format format) {
        return toFloatConstant(format, getValue(), getConstantPool());
    }

    /**
     * Convert this IntConstant to o Float64Constant.
     *
     * @return Float64Constant constants
     */
    public Float64Constant toFloat64Constant() {
        PackedInteger pi = getValue();
        return new Float64Constant(getConstantPool(),
                pi.isBig() ? pi.getBigInteger().doubleValue() : (double) pi.getLong());
    }

    /**
     * Convert the specified PackedInteger to an IntConstant of the specified format.
     *
     * @param format  the format of the IntConstant to use
     * @param pi      the PackedInteger value
     * @param pool    the ConstantPool to use
     *
     * @return an IntConstant
     *
     * @throws ArithmeticException  on overflow
     */
    public static IntConstant toIntConstant(Format format, PackedInteger pi, ConstantPool pool) {
        if (   pi.compareTo(IntConstant.getMinLimit(format)) < 0
            || pi.compareTo(IntConstant.getMaxLimit(format)) > 0) {
            throw new ArithmeticException("out of range: " + pi);
        }

        return pool.ensureIntConstant(pi, format);
    }

    /**
     * Convert the specified PackedInteger to a DecimalConstant of the specified format.
     *
     * @param format  the format of the DecimalConstant to use
     * @param pi      the PackedInteger value
     * @param pool    the ConstantPool to use
     *
     * @return a DecimalConstant
     */
    public static DecimalConstant toDecConstant(Format format, PackedInteger pi, ConstantPool pool) {
        return switch (format) {
            case Dec64 -> pool.ensureDecConstant(
                   new Decimal64(new BigDecimal(pi.getBigInteger(), MathContext.DECIMAL64)));
            default -> null; // the error will be reported by the compiler
        };
    }

    /**
     * Convert the specified PackedInteger to a FloatConstant of the specified format.
     *
     * @param format  the format of the FloatConstant to use
     * @param pi      the PackedInteger value
     * @param pool    the ConstantPool to use
     *
     * @return a FloatConstant
     */
    public static FloatConstant toFloatConstant(Format format, PackedInteger pi, ConstantPool pool) {
        return switch (format) {
            case Float32 -> new Float32Constant(pool, pi.isBig()
                    ? pi.getBigInteger().floatValue()
                    : (float) pi.getLong());
            default -> null;
        };
    }

    @Override
    public Object getLocator() {
        return m_pint;
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof IntConstant)) {
            return -1;
        }
        return this.m_pint.compareTo(((IntConstant) that).m_pint);
    }

    @Override
    public String getValueString() {
        return m_pint.toString();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        m_pint.writeObject(out);
    }

    @Override
    public String getDescription() {
        return "value=" + getValueString();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_pint);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant
     */
    private final Format m_fmt;

    /**
     * The constant integer value stored as a PackedInteger.
     */
    private final PackedInteger m_pint;
}