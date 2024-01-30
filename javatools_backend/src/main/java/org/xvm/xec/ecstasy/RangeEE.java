package org.xvm.xec.ecstasy;
public class RangeEE extends Range {
  static final RangeEE GOLD = new RangeEE();
  public RangeEE( ) { }         // No arg constructor
  public RangeEE( long lo, long hi ) { super(lo,hi,true,true); }
}
