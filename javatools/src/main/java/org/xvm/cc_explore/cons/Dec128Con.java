package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
  Exploring XEC Constants
 */
public class Dec128Con extends DecCon {
  public Dec128Con( FilePart X ) {
    super(X, toBigDecimal128(X.i64(),X.i64()));
  }

  /**
   * @return the significand of the decimal as a Java <tt>BigInteger</tt>
   */
  public static BigInteger getSignificand(long hi, long lo) {
    // get the first digit (most significant digit)
    int nToG4 = (int) (hi >>> G4_SHIFT);
    int nD0   = (nToG4 & 0b011000) == 0b011000
              ? (nToG4 & 0b000001) + 8
              : (nToG4 & 0b000111);

    // keep only the T portion of the high bits (the low bits are all part of the T portion)
    hi &= LS46BITS;

    // process the remainder of the T portion in the high bits (except for the last 6 bits that
    // overflowed from the low bits)
    long nHSig = nD0;
    if (nHSig != 0 || hi != 0) {
      for (int of = 36; of >= 0; of -= 10) {
        nHSig = nHSig * 1000 + decletToInt((int) (hi >>> of));
      }
    }

    // process the T portion in the low bits (including the 6 LSBs of the high bits)
    long nLSig = 0;
    if (nHSig != 0 || lo != 0) {
      // grab the 6 bits from the 7th declet that overflowed to the "high bits" long, and
      // combine those with the highest 4 bits from the "low bits" long
      nHSig = nHSig * 1000 + decletToInt((int) ((hi << 4) | (lo >>> 60)));

      for (int of = 50; of >= 0; of -= 10) {
        nLSig = nLSig * 1000 + decletToInt((int) (lo >>> of));
      }
    }

    // put the digits from the low and high bits together to form the full significand
    BigInteger bintL = nLSig == 0 ? BigInteger.ZERO : BigInteger.valueOf(nLSig);
    return nHSig == 0 ? bintL : BigInteger.valueOf(nHSig).multiply(BIGINT_10_TO_18TH).add(bintL);
  }

  /**
   * @return the exponent of the decimal as a Java <tt>int</tt>
   */
  public static int getExponent( long hi ) {
    // combination field is 17 bits (from bit 46 to bit 62), including 12 "pure" exponent bits
    int nCombo = (int) (hi >>> 46);
    int nExp   = (nCombo & 0b0_11000_000000000000) == 0b0_11000_000000000000
               ? (nCombo & 0b0_00110_000000000000) >>> 1
               : (nCombo & 0b0_11000_000000000000) >>> 3;

    // pull the rest of the exponent bits out of "pure" exponent section of the combo bits
    // section, and unbias the exponent
    return (nExp | nCombo & 0xFFF) - 6176;
  }

  /**
   * Convert the bits of an IEEE 754 decimal to a Java BigDecimal.
   * @param nBits  a 128-bit value containing an IEEE 754 decimal
   * @return a Java BigDecimal
   */
  public static BigDecimal toBigDecimal128(long hi, long lo) {
    ensureFiniteHighBits(hi);   // Throw if not finite
    BigDecimal dec = new BigDecimal(getSignificand(hi,lo), -getExponent(hi), MathContext.DECIMAL128);
    return (hi<0) ? dec.negate() : dec;
  }
  
  /**
   * Test the passed high 64 bits of a 128-bit decimal to ensure that they are finite; if they are
   * not, throw an exception.
   * @param hi  the high 64 bits of a 128-bit IEEE-754-2008 decimal value
   * @throws NumberFormatException if the decimal is either a NaN or an Infinity value
   */
  private static void ensureFiniteHighBits(long hi) {
    if( (hi & G0_G3_MASK) == G0_G3_MASK )
      throw new NumberFormatException("Not a finite value");
  }


  /** One million million (10^18), in a BigInteger format. */
  private static final BigInteger BIGINT_10_TO_18TH   = new BigInteger("1000000000000000000");
  
  /** The least significant 46 bits. */
  private static final long       LS46BITS            = 0x3FFFFFFFFFFFL;

  /** The amount to shift the G3 bit in  the high 64 bits of a 128-bit IEEE 754 decimal. */
  private static final int        G3_SHIFT            = 59;
  /** The bit mask for the G0-G3 bits of the high 64 bits of a 128-bit IEEE 754 decimal. */
  private static final long       G0_G3_MASK          = 0b1111L << G3_SHIFT;
  /** The amount to shift the G4 bit in the high 64 bits of a 128-bit IEEE 754 decimal   */
  private static final int        G4_SHIFT            = 58;
}
