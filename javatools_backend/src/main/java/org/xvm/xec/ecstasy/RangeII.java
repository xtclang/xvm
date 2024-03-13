package org.xvm.xec.ecstasy;
public class RangeII extends AbstractRange {
  public static final RangeII GOLD = new RangeII();
  public RangeII( ) { }         // No arg constructor
  public RangeII( long lo, long hi ) { super(lo,hi,false,false); }
  public static RangeII construct(long lo, long hi) { return new RangeII(lo,hi); }
}
