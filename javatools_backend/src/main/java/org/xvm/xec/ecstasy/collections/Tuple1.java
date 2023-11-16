package org.xvm.xec.ecstasy.collections;

public abstract class Tuple1 extends Tuple0 {
  public Tuple1() { this(1); }
  public Tuple1(int n) { super(n); }
  public Object at(int i) { return i==0 ? f0() : super.at(i); }
  public void set(int i, Object e) { if( i==0 ) f0(e); else super.set(i,e); }
  public abstract Object f0();
  public abstract void f0(Object e);
}
