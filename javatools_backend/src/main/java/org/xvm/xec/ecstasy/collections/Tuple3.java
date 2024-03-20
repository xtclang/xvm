package org.xvm.xec.ecstasy.collections;
import org.xvm.xec.XTC;

public abstract class Tuple3 extends Tuple2 {
  public Tuple3() { this(3); }
  public Tuple3(int n) { super(n); }
  @Override public XTC at(long i) { return i==2 ? f2() : super.at(i); }
  @Override public void set(long i, XTC e) { if( i==2 ) f2(e); else super.set(i,e); }
  public abstract XTC f2();
  public abstract void f2(XTC e);
}
