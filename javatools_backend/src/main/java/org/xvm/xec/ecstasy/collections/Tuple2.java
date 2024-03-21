package org.xvm.xec.ecstasy.collections;
import org.xvm.xec.XTC;

public abstract class Tuple2 extends Tuple1 {
  public Tuple2() { this(2); }
  public Tuple2(int n) { super(n); }
  @Override public XTC at(long i) { return i==1 ? f1() : super.at(i); }
  @Override public void set(long i, XTC e) { if( i==1 ) f1(e); else super.set(i,e); }
  public abstract XTC f1();
  public abstract void f1(XTC e);
}
