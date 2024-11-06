package org.xvm.xec.ecstasy;
public class RangeEE extends AbstractRange {
  static final RangeEE GOLD = new RangeEE();
  public RangeEE( ) { }         // No arg constructor
  public RangeEE( long lo, long hi ) { super(lo,hi,true,true); }
  public static RangeEE construct(long lo, long hi) { return new RangeEE(lo,hi); }
}
