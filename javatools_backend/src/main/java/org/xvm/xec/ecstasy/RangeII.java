package org.xvm.xec.ecstasy;
public class RangeII extends Range {
  static final int KID = GET_KID(new RangeII());
  public int kid() { return KID; }
  public RangeII( ) { }         // No arg constructor
  public RangeII( long lo, long hi ) { super(lo,hi,false,false); }
}
