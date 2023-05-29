package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
  Exploring XEC Constants
 */
public class Dec64Con extends DecCon {
  public Dec64Con( long dec64 ) { super(dec64); }
  public Dec64Con( CPool X ) { this(X.i64()); }

  
  @Override BigDecimal _bd() { return toBigDecimal64(_dec0); }
  
  /**
   * Test the passed bits to ensure that they are finite; if they are not, throw an exception.
   * @param nBits  the 32-bit IEEE-754-2008 decimal value
   * @return a finite 32-bit IEEE-754-2008 decimal value
   * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
   */
  public static long ensureFiniteBits(long nBits) {
    if ((nBits & G0_G3_MASK) == G0_G3_MASK)
      throw new NumberFormatException("Not a finite value");
    return nBits;
  }

  /**
   * Convert a Java BigDecimal to an IEEE 754 64-bit decimal.
   * @param dec  a Java BigDecimal value
   * @return a Java <tt>long</tt> that contains a 64-bit IEEE 754 decimal value
   * @throws RangeException if the value is out of range
   */
  public static long toLongBits(BigDecimal dec)  {
    dec = dec.round(MathContext.DECIMAL64);

    // obtain the significand
    long nSig = dec.unscaledValue().longValueExact();
    if (nSig < -9999999999999999L || nSig > 9999999999999999L)
      throw new ArithmeticException("significand is >16 digits: " + nSig);

    // bias the exponent (the scale is basically a negative exponent)
    int nExp = 398 - dec.scale();
    if (nExp < 0 || nExp >= 768)
      throw new ArithmeticException("biased exponent is out of range [0,768): " + nExp);

    long nBits = 0;
    if (nSig < 0)
      { nBits = SIGN_BIT; nSig  = -nSig; }

    // store the least significant 8 bits of the exponent into the combo field starting at G5
    // store the least significant 15 decimal digits of the significand in 5 10-bit declets in T
    int nLeft  = (int) (nSig / 1_000_000_000L);
    int nRight = (int) (nSig % 1_000_000_000L);
    nBits |=   (((long) (nExp & 0xFF)                         ) << 50)
      |        (((long) intToDeclet(nLeft  /     1_000 % 1000)) << 40)
      |        (((long) intToDeclet(nLeft              % 1000)) << 30)
      |        (((long) intToDeclet(nRight / 1_000_000 % 1000)) << 20)
      |        (((long) intToDeclet(nRight /     1_000 % 1000)) << 10)
      |        (((long) intToDeclet(nRight             % 1000))      );
    
    // remaining significand of 8 or 9 is stored in G4 as 0 or 1, with remaining exponent stored
    // in G2-G3, and G0-G1 both set to 1; otherwise, remaining significand (3 bits) is stored in
    // G2-G4 with remaining exponent stored in G0-G1
    int nSigRem = nLeft / 1_000_000;
    int nGBits  = nSigRem >= 8                              // G01234
      ? (0b11000 | (nSigRem & 0b00001) | ((nExp & 0b11000_00000) >>> 7))
      : (          (nSigRem & 0b00111) | ((nExp & 0b11000_00000) >>> 5));
    
    return nBits | ((long) nGBits) << G4_SHIFT;
  }

  /**
   * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
   * @param nBits  a 64-bit value containing an IEEE 754 decimal
   * @return a Java BigDecimal
   */
  public static BigDecimal toBigDecimal64(long nBits) {
    ensureFiniteBits(nBits);

    // combination field is 13 bits (from bit 50 to bit 62), including 8 "pure" exponent bits
    int  nCombo = (int) (nBits >>> 50);
    int  nExp   = nCombo & 0xFF;
    long nSig;

    // test G0 and G1
    if( (nCombo & 0b0_11000_00000000) == 0b0_11000_00000000 ) {
      // when the most significant five bits of G are 110xx or 1110x, the leading significand
      // digit d0 is 8+G4, a value 8 or 9, and the leading biased exponent bits are 2*G2 + G3,
      // a value of 0, 1, or 2
      nExp |= ((nCombo & 0b0_00110_00000000) >>> 1);   // shift right 9, but then shift left 8
      nSig  = ((nCombo & 0b0_00001_00000000) >>> 8) + 8;
    } else {
      // when the most significant five bits of G are 0xxxx or 10xxx, the leading significand
      // digit d0 is 4*G2 + 2*G3 + G4, a value in the range 0 through 7, and the leading
      // biased exponent bits are 2*G0 + G1, a value 0, 1, or 2; consequently if T is 0 and
      // the most significant five bits of G are 00000, 01000, or 10000, then the value is 0:
      //      v = (-1) S * (+0)
      nExp |= (nCombo & 0b0_11000_00000000) >>> 3;    // shift right 11, but then shift left 8
      nSig  = (nCombo & 0b0_00111_00000000) >>> 8;
    }
    // unbias the exponent
    nExp -= 398;

    // unpack the digits from most significant declet to least significant declet
    nSig = (((((nSig * 1000 + decletToInt((int) (nBits >>> 40)))
                     * 1000 + decletToInt((int) (nBits >>> 30)))
                     * 1000 + decletToInt((int) (nBits >>> 20)))
                     * 1000 + decletToInt((int) (nBits >>> 10)))
                     * 1000 + decletToInt((int) (nBits       )))
                     * (((nBits & SIGN_BIT) >> 63) | 1);            // apply sign

    return new BigDecimal(BigInteger.valueOf(nSig), -nExp, MathContext.DECIMAL64);
  }

  /** The sign bit for a 64-bit IEEE 754 decimal. */
  private static final long       SIGN_BIT        = 1L << 63;

  /** The amount to shift the G3 bit of a 64-bit IEEE 754 decimal.  */
  private static final int        G3_SHIFT        = 59;

  /** The bit mask for the G0-G3 bits of a 64-bit IEEE 754 decimal. */
  private static final long       G0_G3_MASK      = 0b1111L << G3_SHIFT;

  /** The amount to shift the G4 bit of a 64-bit IEEE 754 decimal.  */
  private static final int        G4_SHIFT        = 58;

  /** The value for the G0-G4 bits of a 64-bit IEEE 754 decimal that indicate
      that the decimal value is "Not a Number" (NaN).  */
  private static final long       G0_G4_NAN       = 0b11111L << G4_SHIFT;

  /** The value for the G0-G4 bits of a 64-bit IEEE 754 decimal that indicate
      that the decimal value is infinite. */
  private static final long       G0_G4_INF       = 0b11110L << G4_SHIFT;

  /** The amount to shift the G5 bit of a 64-bit IEEE 754 decimal. */
  private static final int        G5_SHIFT        = 57;

  /** The value of the G5 bit that indicates that a 64-bit IEEE 754 decimal is
      a signaling NaN, if the decimal is a NaN.  */
  private static final long       G5_SIGNAL       = 1L << G5_SHIFT;

  /** The decimal value for zero. */
  public static final Dec64Con   POS_ZERO        = new Dec64Con(0x2238000000000000L);

  /** The decimal value for negative zero. */
  public static final Dec64Con   NEG_ZERO        = new Dec64Con(0xA238000000000000L);

  /** The decimal value for positive one (1). */
  public static final Dec64Con   POS_ONE         = new Dec64Con(0x2238000000000001L);

  /** The decimal value for negative one (-1). */
  public static final Dec64Con   NEG_ONE         = new Dec64Con(0xA238000000000001L);

  /** The decimal value for a "quiet" Not-A-Number (NaN). */
  public static final Dec64Con   NaN             = new Dec64Con(G0_G4_NAN);

  /** The decimal value for a signaling Not-A-Number (NaN). */
  public static final Dec64Con   SNaN            = new Dec64Con(G0_G4_NAN | G5_SIGNAL);

  /** The decimal value for positive infinity. */
  public static final Dec64Con   POS_INFINITY    = new Dec64Con(G0_G4_INF);

  /** The decimal value for negative infinity. */
  public static final Dec64Con   NEG_INFINITY    = new Dec64Con(SIGN_BIT | G0_G4_INF);
}
