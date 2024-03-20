package org.xvm.xec.ecstasy.collections;
import org.xvm.xec.XTC;

public abstract class Tuple1 extends Tuple0 {
  public Tuple1() { this(1); }
  public Tuple1(int n) { super(n); }
  @Override public XTC at(long i) { return i==0 ? f0() : super.at(i); }
  @Override public void set(long i, XTC e) { if( i==0 ) f0(e); else super.set(i,e); }
  public abstract XTC f0();
  public abstract void f0(XTC e);
}
