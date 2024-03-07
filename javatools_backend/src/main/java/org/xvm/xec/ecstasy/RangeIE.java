package org.xvm.xec.ecstasy;
public class RangeIE extends AbstractRange {
  static final RangeIE GOLD = new RangeIE();
  public RangeIE( ) { }         // No arg constructor
  public RangeIE( long lo, long hi ) { super(lo,hi,false,true); }
  public static RangeIE construct(long lo, long hi) { return new RangeIE(lo,hi); }
}
