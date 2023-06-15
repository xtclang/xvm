package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
  Exploring XEC Constants
 */
public class Dec64Con extends DecCon {
  public Dec64Con( CPool X ) {
    super(X, toBigDecimal64(X.i64()));
  }

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
}
