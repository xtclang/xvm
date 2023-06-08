package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
  Exploring XEC Constants
 */
public class Flt32Con extends Const {
  public final float _flt;
  public Flt32Con( FilePart X, Format f ) {
    _flt = switch( f ) {
    case  Float32 -> from32 ( X.i32() );
    case BFloat16 -> from16b( X.u16() );
    case  Float16 -> from16 ( X.u16() );
    default -> throw XEC.TODO();
    };
  }

  // Normal serialized float
  private static float from32( int x ) {
    return Float.intBitsToFloat(x);
  }
  
  // Chopped mantissa style
  private static float from16b( int x ) {
    return Float.intBitsToFloat(x << 16);
  }

  // Half precision style
  /**
   * Convert a 16-bit "half precision" floating point to a "full precision" 32-bit float.
   * @param nHalf  the 16-bit floating point value stored in a 16-bit Java int, whose bits are
   *               encoded using the IEEE-754 binary-radix floating point format
   * @return a 32-bit float
   */
  public static float from16(int nHalf) {
    // notes from the IEEE-754 specification:
  
    // left to right bits of a binary floating point number:
    // size        bit ids       name  description
    // ----------  ------------  ----  ---------------------------
    // 1 bit                       S   sign
    // w bits      E[0]..E[w-1]    E   biased exponent
    // t=p-1 bits  d[1]..d[p-1]    T   trailing significant field
  
    // The range of the encoding's biased exponent E shall include:
    // - every integer between 1 and 2^w - 2, inclusive, to encode normal numbers
    // - the reserved value 0 to encode 0 and subnormal numbers
    // - the reserved value 2w - 1 to encode +/-infinity and NaN
  
    // The representation r of the floating-point datum, and value v of the floating-point datum
    // represented, are inferred from the constituent fields as follows:
    // a) If E == 2^w-1 and T != 0, then r is qNaN or sNaN and v is NaN regardless of S
    // b) If E == 2^w-1 and T == 0, then r=v=(-1)^S * (+infinity)
    // c) If 1 <= E <= 2^w-2, then r is (S, (E-bias), (1 + 2^(1-p) * T))
    //    the value of the corresponding floating-point number is
    //        v = (-1)^S * 2^(E-bias) * (1 + 2^(1-p) * T)
    //    thus normal numbers have an implicit leading significand bit of 1
    // d) If E == 0 and T != 0, then r is (S, emin, (0 + 2^(1-p) * T))
    //    the value of the corresponding floating-point number is
    //        v = (-1)^S * 2^emin * (0 + 2^(1-p) * T)
    //    thus subnormal numbers have an implicit leading significand bit of 0
    // e) If E == 0 and T ==0, then r is (S, emin, 0) and v = (-1)^S * (+0)
  
    // parameter                                      bin16  bin32
    // --------------------------------------------   -----  -----
    // k, storage width in bits                         16     32
    // p, precision in bits                             11     24
    // emax, maximum exponent e                         15    127
    // bias, E-e                                        15    127
    // sign bit                                          1      1
    // w, exponent field width in bits                   5      8
    // t, trailing significant field width in bits      10     23
  
    // a quick & dirty implementation:
    // int nS = (nHalf >>> 15) & 0x1;
    // int nE = (nHalf >>> 10) & 0x1F;
    // int nT = (nHalf       ) & 0x3FF;
    //
    // nE = nE == 0x1F
    //         ? 0xFF  // it's 2^w-1; it's all 1's, so keep it all 1's for the 32-bit float
    //         : nE - 15 + 127;     // adjust the exponent from the 16-bit bias to the 32-bit bias
    //
    // // sign S is now bit 31
    // // exp E is from bit 30 to bit 23
    // // scale T by 13 binary digits (it grew from 10 to 23 bits)
    // return Float.intBitsToFloat(nS << 31 | nE << 23 | nT << 13);
  
    // from: https://stackoverflow.com/questions/6162651/half-precision-floating-point-in-java
    int mant = nHalf & 0x03ff;  // 10 bits mantissa
    int exp  = nHalf & 0x7c00;  // 5 bits exponent
    if (exp == 0x7c00) {        // NaN/Inf
      exp = 0x3fc00;            // -> NaN/Inf
    } else if (exp != 0) {      // normalized value
      exp += 0x1c000;           // exp - 15 + 127
      if (mant == 0 && exp > 0x1c400) { // smooth transition
        return Float.intBitsToFloat((nHalf & 0x8000) << 16 | exp << 13 | 0x3ff);
      }
    } else if (mant != 0) {     // && exp==0 -> subnormal
      exp = 0x1c400;            // make it normal
      do {
        mant <<= 1;             // mantissa * 2
        exp   -= 0x400;         // decrease exp by 1
      }
      while ((mant & 0x400) == 0); // while not normal
      mant &= 0x3ff;               // discard subnormal bit
    }                              // else +/-0 -> +/-0
    return Float.intBitsToFloat(   // combine all parts
            (nHalf & 0x8000) << 16 // sign  << ( 31 - 15 )
            | (exp | mant) << 13); // value << ( 23 - 10 )
  }

}
