package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
  Exploring XEC Constants
 */
public class Dec32Con extends DecCon {
  public Dec32Con( FilePart X ) {
    super(X, toBigDecimal32(X.i32()));
  }

  /**
   * Test the passed bits to ensure that they are finite; if they are not, throw an exception.
   * @param nBits  the 32-bit IEEE-754-2008 decimal value
   * @return a finite 32-bit IEEE-754-2008 decimal value
   * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
   */
  public static int ensureFiniteBits(int nBits) {
    if ((nBits & G0_G3_MASK) == G0_G3_MASK)
      throw new NumberFormatException("Not a finite value");
    return nBits;
  }

  /**
   * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
   * @param nBits  a 32-bit value containing an IEEE 754 decimal
   * @return a Java BigDecimal
   */
  public static BigDecimal toBigDecimal32(int nBits) {
    ensureFiniteBits(nBits);

    // combination field is 11 bits (from bit 20 to bit 30), including 6 "pure" exponent bits
    int nCombo = nBits >>> 20;
    int nExp   = nCombo & 0b111111;
    int nSig;

    // test G0 and G1
    if ((nCombo & 0b011000000000) == 0b011000000000) {
      // when the most significant five bits of G are 110xx or 1110x, the leading significand
      // digit d0 is 8+G4, a value 8 or 9, and the leading biased exponent bits are 2*G2 + G3,
      // a value of 0, 1, or 2
      nExp |= ((nCombo & 0b000110000000) >>> 1);    // shift right 7, but then shift left 6
      nSig  = ((nCombo & 0b000001000000) >>> 6) + 8;
    } else {
      // when the most significant five bits of G are 0xxxx or 10xxx, the leading significand
      // digit d0 is 4*G2 + 2*G3 + G4, a value in the range 0 through 7, and the leading
      // biased exponent bits are 2*G0 + G1, a value 0, 1, or 2; consequently if T is 0 and
      // the most significant five bits of G are 00000, 01000, or 10000, then the value is 0:
      //      v = (-1) S * (+0)
      nExp |= (nCombo & 0b011000000000) >>> 3;    // shift right 9, but then shift left 6
      nSig  = (nCombo & 0b000111000000) >>> 6;
    }

    // unbias the exponent
    nExp -= 101;

    // unpack the digits from most significant declet to least significan declet
    nSig = ((nSig * 1000 + decletToInt(nBits >>> 10))
                  * 1000 + decletToInt(nBits       ))
                  * (((nBits & SIGN_BIT) >> 31) | 1);       // apply sign

    return new BigDecimal(BigInteger.valueOf(nSig), -nExp, MathContext.DECIMAL32);
  }

  /** The sign bit for a 32-bit IEEE 754 decimal. */
  private static final int      SIGN_BIT     = 0x80000000;
  
  /** The amount to shift the G3 bit of a 32-bit IEEE 754 decimal. */
  private static final int      G3_SHIFT     = 27;
  
  /** The bit mask for the G0-G3 bits of a 32-bit IEEE 754 decimal. */
  private static final int      G0_G3_MASK   = 0b1111 << G3_SHIFT;  

}
