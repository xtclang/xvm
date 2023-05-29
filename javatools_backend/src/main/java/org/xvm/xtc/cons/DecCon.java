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
public abstract class DecCon extends TCon {
  public final long _dec0;
  private BigDecimal _bd;
  
  public DecCon( long dec ) { _dec0 = dec; }

  BigDecimal bd() { return _bd==null ? (_bd=_bd()) : _bd; }
  abstract BigDecimal _bd();

  public String asStr() { return bd().toString(); }
  
    /**
     * Convert the three least significant decimal digits of the passed integer value to a declet.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.4.
     *
     * @param nDigits  the int value containing the digits
     *
     * @return a declet
     */
    public static int intToDeclet(int nDigits)
        {
        return digitsToDeclet((nDigits / 100) % 10, (nDigits / 10) % 10, nDigits % 10);
        }

    /**
     * Convert three decimal digits to a declet.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.4.
     *
     * @param d1  4-bit value "d1" from table 3.4 (most significant digit)
     * @param d2  4-bit value "d2" from table 3.4
     * @param d3  4-bit value "d3" from table 3.4 (least significant digit)
     *
     * @return a declet
     */
    public static int digitsToDeclet(int d1, int d2, int d3)
        {
        switch ((d1 & 0b1000) >>> 1 | (d2 & 0b1000) >>> 2 | (d3 & 0b1000) >>> 3)
            {
            // table 3.4        (const 1's)
            // d1.0 d2.0 d3.0   b0123456789   b0 b1 b2                         b3 b4 b5                         b7 b8 b9
            // --------------  ------------   ------------------------------   ------------------------------   ----------
            case 0b000: return 0b0000000000 | (d1 & 0b111)              << 7 | (d2 & 0b111)              << 4 | d3 & 0b111;
            case 0b001: return 0b0000001000 | (d1 & 0b111)              << 7 | (d2 & 0b111)              << 4 | d3 & 0b001;
            case 0b010: return 0b0000001010 | (d1 & 0b111)              << 7 | (d3 & 0b110 | d2 & 0b001) << 4 | d3 & 0b001;
            case 0b011: return 0b0001001110 | (d1 & 0b111)              << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b100: return 0b0000001100 | (d3 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b111)              << 4 | d3 & 0b001;
            case 0b101: return 0b0000101110 | (d2 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b110: return 0b0000001110 | (d3 & 0b110 | d1 & 0b001) << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;
            case 0b111: return 0b0001101110 | (d1 & 0b001)              << 7 | (d2 & 0b001)              << 4 | d3 & 0b001;

            default:
                throw new IllegalArgumentException("d1=" + d1 + ", d2=" + d2 + ", d3=" + d3);
            }
        }

    /**
     * Convert the passed declet to three decimal digits, and return each of them in the three least
     * significant bytes of a Java <tt>int</tt>.
     * <p/>
     * Details are in IEEE 754-2008 section 3.5.2, table 3.3.
     *
     * @param nBits  a declet
     *
     * @return three decimal digits in a Java <tt>int</tt>, such that bits 0-7 contain the least
     *         significant digit, bits 8-15 the second, and bits 16-23 the most significant digit
     */
    public static int decletToDigits(int nBits)
        {
        //               b6 b7 b8                b3 b4
        switch ((nBits & 0b1110) << 1 | (nBits & 0b1100000) >>> 5)
            {
            //     0xxxx                     b0123456789
            default:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (    ((nBits & 0b0000000111)      ));    // d3 = b7 b8 b9
            //     100xx
            case 0b10000:
            case 0b10001:
            case 0b10010:
            case 0b10011:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     101xx
            case 0b10100:
            case 0b10101:
            case 0b10110:
            case 0b10111:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (    ((nBits & 0b0001100000) >>> 4)      // d3 = b3 b4
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     110xx
            case 0b11000:
            case 0b11001:
            case 0b11010:
            case 0b11011:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (    ((nBits & 0b0001110000) >>> 4)) +   // d2 = b3 b4 b5
                             (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     11100
            case 0b11100:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (    ((nBits & 0b1100000000) >>> 7)      // d3 = b0 b1
                                + ((nBits & 0b0000000001)      ));    //    + b9
            //     11101
            case 0b11101:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (    ((nBits & 0b1100000000) >>> 7)      // d2 = b0 b1
                                + ((nBits & 0b0000010000) >>> 4)) +   //    + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     11110
            case 0b11110:
                return 256 * (    ((nBits & 0b1110000000) >>> 7)) +   // d1 = b0 b1 b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            //     11111
            case 0b11111:
                return 256 * (8 + ((nBits & 0b0010000000) >>> 7)) +   // d1 = 8 + b2
                        16 * (8 + ((nBits & 0b0000010000) >>> 4)) +   // d2 = 8 + b5
                             (8 + ((nBits & 0b0000000001)      ));    // d3 = 8 + b9
            }
        }

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
