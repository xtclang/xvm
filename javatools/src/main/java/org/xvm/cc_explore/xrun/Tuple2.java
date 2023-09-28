package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;
public abstract class Tuple2 extends Tuple {
  public Tuple2() { super(2); }
  public Object at(int i) {
    return switch( i ) {
    case 0 -> f0();
    case 1 -> f1();
    default -> throw XEC.TODO();
    };
  }
  public final void set(int i, Object e) {
    switch( i ) {
    case 0 -> f0(e);
    case 1 -> f1(e);
    default -> throw XEC.TODO();
    };
  }
  public abstract Object f0();
  public abstract Object f1();
  public abstract void f0(Object e);
  public abstract void f1(Object e);
}

