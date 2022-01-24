const BFloat16
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "brain float 16") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert:bounds bits.size == 16;
        super(bits);
        }

    /**
     * Construct a 16-bit "brain" floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert:bounds bytes.size == 2;
        super(bytes);
        }

    /**
     * Construct the floating point number from its constituent pieces: A sign bit, a significand,
     * and an exponent.
     *
     * REVIEW GG CP if this approach is good, then replicate (including static properties below) to all FPNumber implementations
     *
     * @param signBit      true if explicitly negative
     * @param significand  the significand value, in the range `0` to
     * @param exponent     the exponent value, in the range [EMIN] to [EMAX]
     */
    construct(Boolean signBit, Int significand, Int exponent)
        {
        if (significand == 0)
            {
            construct BFloat16(signBit ? BFloat16:-0.bits : BFloat16:0.bits);
            return;
            }

        // note that this allows one more significant bit than can be stored, because IEEE754 uses
        // an implicit leading '1' bit (for non-zero, and non-sub-normal values) that is not encoded
        // into the resulting IEEE754 value
        Int sigCount = 64 - significand.leadingZeroCount;
        assert:bounds significand > 0 && sigCount - 1 <= SIG_BITS;
        assert:bounds EMIN <= exponent <= EMAX;

        Bit[] signBits = signBit ? [1] : [0];
        Bit[] expBits  = (exponent + BIAS).toBitArray()[64-EXP_BITS..64);
        Bit[] sigBits  = (significand << (SIG_BITS - (sigCount-1))).toBitArray()[64-SIG_BITS..64);
        construct BFloat16(signBits + expBits + sigBits);
        }




    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedByteLength()
        {
        return True, 2;
        }

    @Override
    static BFloat16 zero()
        {
        return 0.0;
        }

    @Override
    static BFloat16 one()
        {
        return 1.0;
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        TODO need to think this through carefully because there is a sign bit and both +/-0
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op BFloat16 add(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 sub(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 mul(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 div(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 mod(BFloat16 n)
        {
        TODO
        }

    @Override
    BFloat16 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op BFloat16 neg()
        {
        TODO
        }

    @Override
    BFloat16 pow(BFloat16 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    /**
     * Number of significand bits in a BFloat16 value (7).
     */
    static Int SIG_BITS = 7;

    /**
     * Number of exponent bits in a BFloat16 value (8).
     */
    static Int EXP_BITS = 8;

    /**
     * Maximum exponent value in a BFloat16 value (127).
     */
    static Int EMAX     = (1 << (EXP_BITS-1)) - 1;

    /**
     * Minimum exponent value in a BFloat16 value (-126).
     */
    static Int EMIN     = 1 - EMAX;

    /**
     * Exponent bias for a BFloat16 value (126).
     */
    static Int BIAS     = EMAX;

    @Override
    Int emax.get()
        {
        return EMAX;
        }

    @Override
    Int emin.get()
        {
        return EMIN;
        }

    @Override
    Int bias.get()
        {
        return BIAS;
        }

    @Override
    Int significandBitLength.get()
        {
        return SIG_BITS;
        }

    @Override
    Int exponentBitLength.get()
        {
        return EXP_BITS;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean signBit, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    BFloat16 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    BFloat16 floor()
        {
        TODO
        }

    @Override
    BFloat16 ceil()
        {
        TODO
        }

    @Override
    BFloat16 exp()
        {
        TODO
        }

    @Override
    BFloat16 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    BFloat16 log()
        {
        TODO
        }

    @Override
    BFloat16 log2()
        {
        TODO
        }

    @Override
    BFloat16 log10()
        {
        TODO
        }

    @Override
    BFloat16 sqrt()
        {
        TODO
        }

    @Override
    BFloat16 cbrt()
        {
        TODO
        }

    @Override
    BFloat16 sin()
        {
        TODO
        }

    @Override
    BFloat16 cos()
        {
        TODO
        }

    @Override
    BFloat16 tan()
        {
        TODO
        }

    @Override
    BFloat16 asin()
        {
        TODO
        }

    @Override
    BFloat16 acos()
        {
        TODO
        }

    @Override
    BFloat16 atan()
        {
        TODO
        }

    @Override
    BFloat16 atan2(BFloat16 y)
        {
        TODO
        }

    @Override
    BFloat16 sinh()
        {
        TODO
        }

    @Override
    BFloat16 cosh()
        {
        TODO
        }

    @Override
    BFloat16 tanh()
        {
        TODO
        }

    @Override
    BFloat16 asinh()
        {
        TODO
        }

    @Override
    BFloat16 acosh()
        {
        TODO
        }

    @Override
    BFloat16 atanh()
        {
        TODO
        }

    @Override
    BFloat16 deg2rad()
        {
        TODO
        }

    @Override
    BFloat16 rad2deg()
        {
        TODO
        }

    @Override
    BFloat16 nextUp()
        {
        TODO
        }

    @Override
    BFloat16 nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    BFloat16! toBFloat16()
        {
        return this;
        }

    @Override
    IntN toIntN()
        {
        TODO
        }

    @Override
    UIntN toUIntN()
        {
        TODO
        }

    @Override
    FloatN toFloatN()
        {
        TODO
        }

    @Override
    DecN toDecN()
        {
        TODO
        }
    }
