package org.xvm.cc_explore.xrun;
import org.xvm.cc_explore.XEC;
public abstract class Tuple3 extends Tuple {
  final int len() { return 3; }
  public abstract Object f0();
  public abstract Object f1();
  public abstract Object f2();
  final Object get(int i) {
    return switch( i ) {
    case 0 -> f0();
    case 1 -> f1();
    case 2 -> f2();
    default -> throw XEC.TODO();
    };
  }
}

