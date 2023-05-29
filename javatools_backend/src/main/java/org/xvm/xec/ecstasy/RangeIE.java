package org.xvm.xec.ecstasy;
public class RangeIE extends Range {
  static final RangeIE GOLD = new RangeIE();
  public RangeIE( ) { }         // No arg constructor
  public RangeIE( long lo, long hi ) { super(lo,hi,false,true); }
}
