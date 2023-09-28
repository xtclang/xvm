package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.XClz;

import java.util.Objects;

public abstract class Tuple extends XClz {
  // Array-like interface
  abstract int len();
  abstract Object get(int i);

  public final String toString() {
    SB sb = new SB().p("( ");
    int len = len();
    for( int i=0; i<len; i++ )
      sb.p(get(i).toString()).p(", ");
    return sb.unchar(2).p(")").toString();
  }

  
  @Override
  public Mutability mutability$get() {
    int len = len();
    for( int i=0; i<len; i++ ) {
      Object o = get(i);
      // These things are all Constant mutability
      if( o instanceof Number ) continue; // ints, longs, doubles
      if( o instanceof String ) continue;
      if( o instanceof Character ) continue;
      throw XEC.TODO();
    }
    
    return Mutability.Constant;
  }

  // Converts the underlying Tuple to a TupleN with the extra field.
  // Loses all compiler knowledge of the types.
  public Tuple add(Class clz, Object x) {
    // TODO: if the underlying class is already a TupleN we can use
    // Arrays.copyOf instead of a manual copy.
    int len = len();
    Object[] es = new Object[len+1];
    for( int i=0; i<len(); i++ )
      es[i] = get(i);
    es[len] = x;
    return new TupleN(es);
  }

  @Override public boolean equals( Object o ) {
    if( o==this ) return true;
    if( !(o instanceof Tuple that) ) return false;
    int len0 = this.len();
    int len1 = that.len();
    if( len0 != len1 ) return false;
    for( int i=0; i<len0; i++ )
      if( !Objects.equals(this.get(i),that.get(i)) )
        return false;
    return true;
  }
}
