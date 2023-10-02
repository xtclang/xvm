module TestDec28 {
    @Inject Console console;

    void run() {
        assert:debug;

        for (String s : ["0", "1", "123", "123.45", "1.234567", "0.0001234567",
                         ".00001234500", "1234000"]) {
            Dec28 dec = new Dec28(s);
            console.print($"s={s}, dec={dec}");
        }
    }

/**
 * Implementation details:
 *
 *     S --G (combo)-- -------T (trailing significand)--------
 *       ---w+5 bits-- ----------J*10 bits--------------------
 *       G0         G6
 *     0 1 2 3 4 5 6 7 8                                    27
 *
 *     2 2 2 2 2 2 2 2 1 1 1 1 1 1 1 1 1 1 0 0 0 0 0 0 0 0 0 0
 *     7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0
 *
 *     J=2
 *     k=28    1+5+w+t     =32×ceiling((p+2)/9)    storage width, in bits
 *     bias    E−q         =emax+p–2
 *     w=2     k–t−6       =k/16+4
 *     w+5=7               =k/16+9                 combination field width in bits
 *     t=20    k–w−6       =15×k/16−10             trailing significand field width in bits
 *     p=7     3×t/10+1    =9×k/32−2               precision, in digits
 *     emax=6  3×2^(w−1)
 *     emin=-5 1 − emax
 *     bias=   emax+p−2
 *
 *     i) If G0 and G1 together are one of 00, 01, or 10, then the biased exponent E
 *        is formed from G0 through Gw+1 (G3) and the significand is formed from bits
 *        Gw+2 (G4) through the end of the encoding (including T).
 *        -> 4 bits of exponent
 *     ii) If G0 and G1 together are 11 and G2 and G3 together are one of 00, 01, or 10,
 *         then the biased exponent E is formed from G2 through Gw+3 (G5) and the
 *         significand is formed by prefixing the 4 bits (8+G(w+4)) (8+G6) to T.
 *         -> 4 bits of exponent
 *
 *     ?11110...   ∞
 *     011110...   +∞
 *     111110...   -∞
 *     ?11111...   NaN
 *     011111...   +NaN
 *     111111...   -NaN
 *     0111111...  +sNaN
 *     1111111...  -sNaN
 *     0111110...  +qNaN
 *     1111110...  -qNaN
 */
const Dec28(Bit[] bits) {
    construct(Bit[] bits) {
        assert:arg bits.size == 28;
        this.bits = bits.freeze();
        }

    construct(String lit) {
        Boolean neg = False;
        UInt32  sig = 0;
        Boolean dot = False;
        Int     ldc = 0;        // leading digit count
        Int     lzc = 0;        // leading zero count (after the dot, before any non-zero digits)
        Int     tdc = 0;        // trailing digit count
        Boolean any = False;
        Loop: for (Char ch : lit) {
            switch (ch) {
                case '+':
                    assert Loop.count == 0;
                    break;

                case '-':
                    assert Loop.count == 0;
                    neg = True;
                    break;

                case '.':
                    assert !dot;
                    dot=True;
                    break;

                case '0'..'9':
                    any = True;
                    sig = sig * 10 + (ch - '0');
                    if (dot) {
                        if (sig > 0) {
                            ++tdc;
                        } else {
                            ++lzc;
                        }
                    } else if (sig > 0) { // ignore leading zeros
                        ++ldc;
                    }
                    break;

                default:
                    assert as $"unexpected character: {ch.quoted()}";
                }
            }

        assert ldc <= 7            as $"too many digits ({ldc})";
        assert any                 as "no digits";
        assert !dot || lzc+tdc > 0 as "no digits encountered after decimal point";

        Int digits = ldc + lzc + tdc;
        switch (digits) {
            case 0:
                construct Dec28(neg ? NegZero.bits : PosZero.bits);
                return;

            case 1..6:
                while (digits++ < 7) {
                    // add a trailing zero
                    sig *= 10;
                }
                continue;
            case 7:
                construct Dec28(neg, sig, (ldc - 1 - lzc).toInt8());
                return;
            }

        // 8 or more digits; test if some were trailing zeros
        while (True) {
            (val newSig, val digit) = sig /% 10;
            if (digit != 0) {
                break;
                }

            sig = newSig;
            if (--digits == 7) {
                construct Dec28(neg, sig, (ldc - 1 - lzc).toInt8());
                }
            assert --tdc >= 0;
            }

        // only other thing allowed is if it's a small fraction with leading zeros
        assert ldc == 0 && tdc <= 7 as $"too many significant digits ({digits})";
        assert lzc + tdc <= 11      as $|too many digits after the decimal point ({lzc} leading \
                                        |zeros followed by {tdc} non-zero digits)
                                       ;
        construct Dec28(neg, sig, (6 - tdc - lzc).toInt8());
        }

    construct(Boolean negative, UInt32 significand, Int8 exponent) {
        Bit[] bits = new Bit[28];

        assert:arg significand < 10_000_000;
        assert:arg -5 <= exponent <= 6;

        if (negative) {
            bits[0] = 1;
            }

        if (significand != 0) {
            // adjust exponent such that the minimum exponent is stored as a 0
            exponent += 5;

            // separate out the first digit, and copy the remaining 20 bits of data
            UInt32 highBits = significand >> 20;          // <4 bits = ~1 digit
            significand    &= significand & 0x0FFFFF;     // 20 bits = 6+ digits
            bits.replaceAll(8, significand.toBitArray()[12..<32]);

            if (highBits >= 8) {
                bits[1] = 1;
                bits[2] = 1;
                bits.replaceAll(3, exponent.toBitArray()[4..<8]);
                bits[7] = highBits.toBitArray()[31];
                } else {
                bits.replaceAll(1, exponent.toBitArray()[4..<8]);
                bits.replaceAll(5, highBits.toBitArray()[29..<32]);
                }
            }

        construct Dec28(bits);
        }


    // ----- internal -----

    static Dec28 PosZero     = new Dec28([0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 NegZero     = new Dec28([1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 PosNaN      = new Dec28([0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 NegNaN      = new Dec28([1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 PosInfinity = new Dec28([0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 NegInfinity = new Dec28([1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);

    Dec28 normalize() {
        if (!finite) {
            if (infinity) {
                return negative ? NegInfinity : PosInfinity;
                }

            assert NaN;
            return negative ? NegNaN : PosNaN;
            }

        if (significand == 0) {
            return negative ? NegZero : PosZero;
            }

        // TODO?
        return this;
        }

    Boolean S.get() {
        return bits[0].toBoolean();
        }

    Boolean G0.get() {
        return bits[1].toBoolean();
        }

    Boolean G1.get() {
        return bits[2].toBoolean();
        }

    Boolean G2.get() {
        return bits[3].toBoolean();
        }

    Boolean G3.get() {
        return bits[4].toBoolean();
        }

    Boolean G4.get() {
        return bits[5].toBoolean();
        }

    Boolean G5.get() {
        return bits[6].toBoolean();
        }

    Boolean G6.get() {
        return bits[7].toBoolean();
        }

    Int emax.get() {
        return 6;
        }

    Int emin.get() {
        return 1-emax;
        }

    Int bias.get() {
        // emax+p−2 == 11
        return emax+5;
        }

    // -----

    Boolean finite.get() {
        return !(G1 && G2 && G3 && G4);
        }

    Boolean infinity.get() {
        return G1 && G2 && G3 && G4 && !G5;
        }

    Boolean NaN.get() {
        return G1 && G2 && G3 && G4 && G5;
        }

    Signum sign.get() {
        if (finite && significand == 0) {
            return Zero;
            }

        return negative ? Negative : Positive;
        }

    Boolean negative.get() {
        return S;
        }

    (Boolean negative, UInt32 significand, Int8 exponent) split() {
        return negative, significand, exponent;
        }

    UInt32 significand.get() {
        // if G0 and G1 together are one of 00, 01, or 10, then the significand is formed from bits
        // Gw+2 (G4) through the end of the encoding (including T).
        if (!(G0 & G1)) {
            return bits[5..<28].toUInt32();
            }

        // otherwise, if G2 and G3 together are one of 00, 01, or 10, then the significand is formed
        // by prefixing the 4 bits (8+G(w+4)) (8+G6) to T.
        if (!(G2 & G3)) {
            return 0x800000 | bits[7..<28].toUInt32();
            }

        return 0;
        }

    Int8 exponent.get() {
        // if G0 and G1 together are one of 00, 01, or 10, then the biased exponent E is formed from
        // G0 through Gw+1 (G3)
        if (!(G0 & G1)) {
            return bits[1..4].toUInt8().toInt8() + emin.toInt8();
            }

        // otherwise, if G2 and G3 together are one of 00, 01, or 10, then the biased exponent E is
        // formed from G2 through Gw+3 (G5)
        if (!(G2 & G3)) {
            return bits[3..6].toUInt8().toInt8() + emin.toInt8();
            }

        return 0;
        }

    // ----- operators -----------------------------------------------------------------------------

    @Op("-#")
    Dec28 neg() {
        return new Dec28(bits.replace(0, ~bits[0]));
        }

    @Op("+")
    Dec28 add(Dec28 n) {
        if (!(finite && n.finite)) {
            if (NaN) {
                return this;
                }

            if (n.NaN) {
                return n;
                }

            if (infinity) {
                return this;
                }

            assert n.infinity;
            return n;
            }

        (Boolean neg1, UInt32 sig1, Int8 exp1) = this.split();
        if (sig1 == 0) {
            return n;
            }

        (Boolean neg2, UInt32 sig2, Int8 exp2) = n.split();
        if (sig2 == 0) {
            return this;
            }

        if (exp1 != exp2) {
            // TODO scale
            }

        if (neg1 == neg2) {
            sig1 += sig2;
            } else if (sig2 > sig1) {
            sig1 = sig2 - sig1;
            neg1 = !neg1;
            } else {
            sig1 -= sig2;
            }

        if (sig1 > 9999999) {
            if (++exp1 > emax) {
                return neg1 ? NegInfinity : PosInfinity;
                }
            sig1 /= 10;
            }

        return new Dec28(neg1, sig1, exp1);
        }

    @Op("-")
    Dec28 sub(Dec28 n) {
        TODO return new Dec28();
        }

    @Op("*")
    Dec28 mul(Dec28 n) {
        TODO return new Dec28();
        }

    @Op("/")
    Dec28 div(Dec28 n) {
        TODO return new Dec28();
        }

    @Op("%")
    Dec28 mod(Dec28 n) {
        TODO return new Dec28();
        }

    @Override
    String toString() {
        if (!finite) {
            return switch (negative, NaN) {
                    case (False, False): "+∞";
                    case (False, True ): "+NaN";
                    case (True , False): "-∞";
                    case (True , True ): "-NaN";
                };
            }

        (Boolean neg, UInt32 sig, Int8 exp) = this.split();
        if (sig == 0) {
            return neg ? "-0" : "0";
            }

        StringBuffer buf = new StringBuffer(14);
        if (neg) {
            buf.append('-');
            }

        // render up to the first digit of the significand
        Int pow = exp;
        switch (pow <=> -1) {
            case Lesser:
                // for a significand of 1234567, the value is e.g. 0.00001234567
                buf.append('0')
                   .append('.');

                while(pow < -1) {
                    buf.append('0');
                    ++pow;
                }
               break;

            case Equal:
                // for a significand of 1234567, the value is 0.1234567
                buf.append('0');
                break;

            case Greater:
                // begin rendering at the first digit of the significand
                pow = -1;
                break;
            }
        assert pow == -1;     // TODO delete this line eventually

        // render the significand
        static UInt32[] pows = [1000000, 100000, 10000, 1000, 100, 10, 1];
        while (sig != 0 && pow < 6) {
            if (pow == exp) {
                buf.append('.');
                }

            UInt32 digit;
            if (++pow < 6) {
                (digit, sig) = sig /% pows[pow];
                } else {
                digit = sig;
                }

            buf.append(digit);
            }

        // finish any trailing zeros to the left of the decimal point
        while (pow++ < exp) {
            buf.append('0');
            }

        return buf.toString();
        }

    // TODO equals hashcode compare
    }

}