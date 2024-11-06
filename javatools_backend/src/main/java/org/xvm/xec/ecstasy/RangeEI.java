package org.xvm.xec.ecstasy;
public class RangeEI extends AbstractRange {
  static final RangeEI GOLD = new RangeEI();
  public RangeEI( ) { }         // No arg constructor
  public RangeEI( long lo, long hi ) { super(lo,hi,true,false); }
  public static RangeEI construct(long lo, long hi) { return new RangeEI(lo,hi); }
}
