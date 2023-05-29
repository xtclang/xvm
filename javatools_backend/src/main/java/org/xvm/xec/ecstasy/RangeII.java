package org.xvm.xec.ecstasy;
public class RangeII extends Range {
  public static final RangeII GOLD = new RangeII();
  public RangeII( ) { }         // No arg constructor
  public RangeII( long lo, long hi ) { super(lo,hi,false,false); }
}
