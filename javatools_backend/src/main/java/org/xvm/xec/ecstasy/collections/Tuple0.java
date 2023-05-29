package org.xvm.xec.ecstasy.collections;
import org.xvm.XEC;

public class Tuple0 extends Tuple {
  public static final Tuple0 GOLD = new Tuple0();
  
  public Tuple0() { this(0); }
  public Tuple0(int n) { super(n); }
  public Object at(int i) { throw XEC.TODO(); }
  public void set(int i, Object e) { throw XEC.TODO(); }
}
