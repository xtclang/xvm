package org.xvm.xec.ecstasy.collections;
import org.xvm.XEC;

public class Tuple0 extends Tuple {
  static final int KID = GET_KID(new Tuple0());
  public int kid() { return KID; }
  
  public Tuple0() { this(0); }
  public Tuple0(int n) { super(n); }
  public Object at(int i) { throw XEC.TODO(); }
  public void set(int i, Object e) { throw XEC.TODO(); }
}
