package org.xvm.xec.ecstasy;
public class RangeIE extends Range {
  static final int KID = GET_KID(new RangeIE());
  public int kid() { return KID; }
  public RangeIE( ) { }         // No arg constructor
  public RangeIE( long lo, long hi ) { super(lo,hi,false,true); }
}
