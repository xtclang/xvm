package org.xvm.xec.ecstasy.numbers;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xec.ecstasy.collections.Array;
import org.xvm.xec.ecstasy.collections.AryUInt8;
import org.xvm.xec.ecstasy.collections.AryXTC;
import org.xvm.xec.ecstasy.numbers.Bit;
import org.xvm.xrun.Never;
import org.xvm.xtc.cons.Dec64Con;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
     Support XTC Number
*/
public class Dec64 extends DecimalFPNumber {
  public static final Dec64 GOLD = new Dec64((Never)null);
  
  static final MathContext[] MATHCONTEXTS = new MathContext[]{
    new MathContext(64,RoundingMode.HALF_EVEN),
    new MathContext(64,RoundingMode.UP       ),
    new MathContext(64,RoundingMode.CEILING  ),
    new MathContext(64,RoundingMode.DOWN     ),
    new MathContext(64,RoundingMode.FLOOR    ),
  };

  private final long _dec64;
  private final BigDecimal _bd;
  
  public Dec64(Never n ) { _bd=null; _dec64=0; }
  public Dec64(BigDecimal bd) {
    _bd = bd;
    _dec64 = Dec64Con.toLongBits(_bd);
  }    
  public Dec64(String s) { this(new BigDecimal(s,MathContext.DECIMAL64)); }
  public Dec64(double d) { this(new BigDecimal(d)); }
  public static Dec64 construct( AryXTC<Bit> bits ) { return new Dec64(bits); }
  public Dec64(AryXTC<Bit> bits) {
    long dec = 0;
    for( int i=0; i<64; i++ )
      dec = dec | ((bits.at8(i)._b ? 1L : 0L) << i);
    _dec64 = dec;
    _bd = Dec64Con.toBigDecimal64(dec);
  }
  public static Dec64 construct( AryUInt8 ary ) { return new Dec64(ary); }
  public Dec64(AryUInt8 ary) {
    long dec = 0;
    for( int i=0; i<8; i++ )
      dec = dec | ((long)ary.at8(i)<<(i*8));
    _dec64 = dec;
    _bd = Dec64Con.toBigDecimal64(dec);
  }

  public static Dec64 construct( long d ) { return new Dec64((double)d); }
  
  public Dec64 ceil()  { return new Dec64(_bd.setScale(0,RoundingMode.CEILING)); }
  public Dec64 floor() { return new Dec64(_bd.setScale(0,RoundingMode.FLOOR  )); }
  public Dec64 round(DecimalFPNumber.Rounding rnd) {
    return new Dec64(_bd.setScale(0,DecimalFPNumber.ROUNDINGMODE[rnd.ordinal()]));
  }

  public Dec128 toDec128() { throw XEC.TODO(); }
  public AryXTC<Bit> toBitArray(Array.Mutability mut) {
    Bit[] bits = new Bit[64];
    for( int i=0; i<64; i++ )
      bits[i] = new Bit((_dec64>>i)&1);
    return new AryXTC<>(Bit.GOLD,bits);
  }
  public AryUInt8 toByteArray(Array.Mutability mut) {
    byte[] bs = new byte[8];
    for( int i=0; i<8; i++ )
      bs[i] = (byte)(_dec64>>(i*8));
    return new AryUInt8(mut,bs);
  }
  
  // --- Stringable
  @Override public String toString() { return _bd.toString(); }
  // --- Comparable
  @Override public boolean equals( XTC x0, XTC x1 ) {
    return ((Dec64)x0)._dec64==(((Dec64)x1)._dec64);
  }
}
