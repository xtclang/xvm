package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;
public abstract class Tuple3 extends Tuple2 {
  public Tuple3() { this(3); }
  public Tuple3(int n) { super(n); }
  public Object at(int i) { return i==2 ? f2() : super.at(i); }
  public void set(int i, Object e) { if( i==2 ) f2(e); else super.set(i,e); }
  public abstract Object f2();
  public abstract void f2(Object e);
}
