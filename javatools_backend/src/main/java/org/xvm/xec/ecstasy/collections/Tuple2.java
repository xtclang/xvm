package org.xvm.xec.ecstasy.collections;

public abstract class Tuple2 extends Tuple1 {
  public Tuple2() { this(2); }
  public Tuple2(int n) { super(n); }
  public Object at(int i) { return i==1 ? f1() : super.at(i); }
  public void set(int i, Object e) { if( i==1 ) f1(e); else super.set(i,e); }
  public abstract Object f1();
  public abstract void f1(Object e);
}
