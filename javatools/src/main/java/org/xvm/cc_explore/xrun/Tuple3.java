package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;
public abstract class Tuple3 extends Tuple {
  public final int len() { return 3; }
  public final Object at(int i) {
    return switch( i ) {
    case 0 -> f0();
    case 1 -> f1();
    case 2 -> f2();
    default -> throw XEC.TODO();
    };
  }
  public final void set(int i, Object e) {
    switch( i ) {
    case 0 -> f0(e);
    case 1 -> f1(e);
    case 2 -> f2(e);
    default -> throw XEC.TODO();
    };
  }
  public abstract Object f0();
  public abstract Object f1();
  public abstract Object f2();
  public abstract void f0(Object e);
  public abstract void f1(Object e);
  public abstract void f2(Object e);
}

