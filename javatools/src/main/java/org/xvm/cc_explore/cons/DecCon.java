package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.tvar.TVBase;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
  Exploring XEC Constants
 */
public abstract class DecCon extends TCon {
  private final BigDecimal _dec;
  public DecCon( CPool X, BigDecimal dec ) { _dec = dec; }
  @Override TVBase _setype( ) { return new TVBase(this); }
  

  /**
   * Convert the passed declet to three decimal digits, and format them as a Java <tt>int</tt> in
   * the range 0-999.
   * <p/>
   * Details are in IEEE 754-2008 section 3.5.2, table 3.3.
   * @param nBits  a declet
   * @return three decimal digits in a Java <tt>int</tt> (000-999)
   */
  public static int decletToInt(int nBits) {
    //               b6 b7 b8                b3 b4
    switch ((nBits & 0b1110) << 1 | (nBits & 0b1100000) >>> 5)  {
      //     0xxxx                 b0123456789
    default:
      return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
              10 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
              (    ((nBits & 0b0000000111)      ));    // d3 = b7 b8 b9
      //     100xx
    case 0b10000:
    case 0b10001:
    case 0b10010:
    case 0b10011:
      return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
              10 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                   (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
      //     101xx
    case 0b10100:
    case 0b10101:
    case 0b10110:
    case 0b10111:
      return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
              10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                   (    ((nBits & 0b0001100000) >>> 4)      // d3 = b3 b4
                      + ((nBits & 0b0000000001)      ));    //    + b9
    //     110xx
    case 0b11000:
    case 0b11001:
    case 0b11010:
    case 0b11011:
       return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
               10 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                    (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                       + ((nBits & 0b0000000001)      ));    //    + b9
    //     11100
    case 0b11100:
      return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
              10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                   (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                      + ((nBits & 0b0000000001)      ));    //    + b9
    //     11101
    case 0b11101:
      return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
              10 * (    ((nBits & 0b1100000000) >>> 7)      // d2 = b0 b1
                      + ((nBits & 0b0000010000) >>> 4)) +   //    + b5
                   (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
    //     11110
    case 0b11110:
      return 100 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
              10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                   (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
    //     11111
    case 0b11111:
      return 100 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
              10 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                   (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
    }
  }

}
