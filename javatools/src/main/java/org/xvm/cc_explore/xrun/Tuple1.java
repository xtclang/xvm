package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;
public abstract class Tuple1 extends Tuple {
  public Tuple1() { super(1); }
  public Object at(int i) {
    return switch( i ) {
    case 0 -> f0();
    default -> throw XEC.TODO();
    };
  }
  public final void set(int i, Object e) {
    switch( i ) {
    case 0 -> f0(e);
    default -> throw XEC.TODO();
    };
  }
  public abstract Object f0();
  public abstract void f0(Object e);
}
