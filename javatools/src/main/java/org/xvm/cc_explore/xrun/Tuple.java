package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.XClz;
import static org.xvm.cc_explore.xclz.XClz.Mutability.*;

import java.util.Objects;

public abstract class Tuple extends XClz implements Cloneable {
  private byte _mut = -1;
  // Array-like interface
  abstract public int len();
  abstract public Object at(int i);
  abstract public void set(int i, Object o);

  public final String toString() {
    SB sb = new SB().p("( ");
    int len = len();
    for( int i=0; i<len; i++ )
      sb.p(at(i).toString()).p(", ");
    return sb.unchar(2).p(")").toString();
  }

  
  @Override
  public Mutability mutability$get() {
    if( _mut == -1 ) {
      int len = len();
      for( int i=0; i<len; i++ ) {
        Object o = at(i);
        // These things are all Constant mutability
        if( o instanceof Number ) continue; // ints, longs, doubles
        if( o instanceof String ) continue;
        if( o instanceof Character ) continue;
        throw XEC.TODO();
      }
      _mut = (byte)Mutability.Constant.ordinal();
    }
    return Mutability.VALUES[_mut];
  }

  // Converts the underlying Tuple to a TupleN with the extra field.
  // Loses all compiler knowledge of the types.
  public Tuple add(Class clz, Object x) {
    // TODO: if the underlying class is already a TupleN we can use
    // Arrays.copyOf instead of a manual copy.
    int len = len();
    Object[] es = new Object[len+1];
    for( int i=0; i<len(); i++ )
      es[i] = at(i);
    es[len] = x;
    return new TupleN(es);
  }

  // Crazy contract... copied from XTC Tuple.x comments
  // 
  public Tuple ensureMutability(Mutability mut0, boolean inPlace) {
    Mutability mut = mutability$get();
    if( mut==mut0 ) return this;
    if( mut==Constant && mut0==Persistent ) return this;
    if( mut==Fixed && mut0==Persistent && inPlace ) throw XEC.TODO(); // Update self to Persistent
    if( mut0==Constant ) throw XEC.TODO();                            // Deep freeze in place
    // Copy, then set mutability
    try {
      Tuple clone = (Tuple)clone();
      clone._mut = (byte)mut0.ordinal();
      return clone;
    } catch( CloneNotSupportedException cnse ) {throw new RuntimeException(cnse); }
  }

  // Recursive element-wise equality
  @Override public boolean equals( Object o ) {
    if( o==this ) return true;
    if( !(o instanceof Tuple that) ) return false;
    int len0 = this.len();
    int len1 = that.len();
    if( len0 != len1 ) return false;
    for( int i=0; i<len0; i++ )
      if( !Objects.equals(this.at(i),that.at(i)) )
        return false;
    return true;
  }
}
