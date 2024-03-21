package org.xvm.xec.ecstasy.collections;
import org.xvm.xec.XTC;

public abstract class Tuple4 extends Tuple3 {
  public Tuple4() { this(4); }
  public Tuple4(int n) { super(n); }
  @Override public XTC at(long i) { return i==3 ? f3() : super.at(i); }
  @Override public void set(long i, XTC e) { if( i==3 ) f3(e); else super.set(i,e); }
  public abstract XTC f3();
  public abstract void f3(XTC e);
}
