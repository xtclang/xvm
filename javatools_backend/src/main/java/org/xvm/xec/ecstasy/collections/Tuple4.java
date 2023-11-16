package org.xvm.xec.ecstasy.collections;

public abstract class Tuple4 extends Tuple3 {
  public Tuple4() { this(4); }
  public Tuple4(int n) { super(n); }
  public Object at(int i) { return i==3 ? f3() : super.at(i); }
  public void set(int i, Object e) { if( i==3 ) f3(e); else super.set(i,e); }
  public abstract Object f3();
  public abstract void f3(Object e);
}
