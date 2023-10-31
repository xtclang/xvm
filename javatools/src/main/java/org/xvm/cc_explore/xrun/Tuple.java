package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.cons.TCon;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.xclz.XClz;
import org.xvm.cc_explore.xclz.XType;

import static org.xvm.cc_explore.xclz.XClz.Mutability.*;

import java.util.Objects;
import java.util.HashMap;

public abstract class Tuple extends XClz implements Cloneable {
  private byte _mut = -1;
  final short _len; // 0-32767 length limit
  // Array-like interface
  abstract public Object at(int i); // At an index
  abstract public void set(int i, Object o); // Set an index

  Tuple(int len) { _len=(short)len; }

  // Select a range using array syntax.
  // Loses all Java compiler knowledge of the types.
  public Tuple at(Range r) {
    Object es[] = new Object[(int)(r._hi - r._lo)];
    for( int i=0; i<es.length; i++ )
      es[i] = at((int)(i+r._lo));
    Tuple t = new TupleN(es);
    t._mut = _mut;              // Same mutability
    return t;
  }
  // Using slice call instead of array syntax
  public final Tuple slice(Range r) { return at(r); }
  
  @Override
  public final String toString() {
    SB sb = new SB().p("( ");
    for( int i=0; i<_len; i++ )
      sb.p(at(i).toString()).p(", ");
    return sb.unchar(2).p(")").toString();
  }

  @Override
  public Mutability mutability$get() {
    if( _mut == -1 ) {          // Not cached?  Compute once
      for( int i=0; i<_len; i++ ) {
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
  // Loses all Java compiler knowledge of the types.
  public Tuple add(Class clz, Object x) {
    // TODO: if the underlying class is already a TupleN we can use
    // Arrays.copyOf instead of a manual copy.
    Object[] es = new Object[_len+1];
    for( int i=0; i<_len; i++ )
      es[i] = at(i);
    es[_len] = x;
    return new TupleN(es);
  }

  // Loses all Java compiler knowledge of the types.
  public Tuple addAll( Tuple tup ) {
    Object[] es = new Object[_len + tup._len];
    for( int i=0; i<_len; i++ )
      es[i] = at(i);
    for( int i=0; i<tup._len; i++ )
      es[i+_len] = tup.at(i);
    // TODO: Complex default mutability
    return new TupleN(es);
  }
  
  // Crazy contract... copied from XTC Tuple.x comments
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
    if( this._len != that._len ) return false;
    for( int i=0; i<_len; i++ )
      if( !Objects.equals(this.at(i),that.at(i)) )
        return false;
    return true;
  }


  // Return a tuple class for this set of types.  The class is cached, and can
  // be used many times.
  public static XType.JTupleType make_class( HashMap<String,String> cache, TCon[] parms ) {
    int N = parms==null ? 0 : parms.length;
    XType[] clzs = new XType[N];
    for( int i=0; i<N; i++ )
      clzs[i]=XType.xtype(parms[i],false);
    XType.JTupleType xtt = XType.JTupleType.make(clzs);

    // Lookup cached version
    if( N==0 ) return xtt;     // Tuple0 already exists in the base runtime
    String tclz = xtt.clz();
    if( !cache.containsKey(tclz) ) {
      /* Gotta build one.  Looks like:
         class Tuple3$long$String$char extends Tuple3 {
           public long _f0;
           public String _f1;
           public char _f2;
           Tuple(long f0, String f1, char f2) {
             _f0=f0; _f1=f1; _f2=f2;
           }
           public Object f0() { return _f0; }
           public Object f1() { return _f1; }
           public Object f2() { return _f2; }
         }
      */
      // Tuple N class
      SB sb = new SB();
      sb.p("class ").p(tclz).p(" extends Tuple"+N+" {").nl().ii();
      // N field declares
      for( int i=0; i<N; i++ )
        sb.ip("public ").p(clzs[i].toString()).p(" _f").p(i).p(";").nl();
      // Constructor, taking N arguments
      sb.ip(tclz).p("( ");
      for( int i=0; i<N; i++ )
        sb.p(clzs[i].toString()).p(" f").p(i).p(", ");
      sb.unchar(2).p(") {").nl().ii().i();
      // N arg to  field assigns
      for( int i=0; i<N; i++ )
        sb.p("_f").p(i).p("=").p("f").p(i).p("; ");
      sb.nl().di().ip("}").nl();
      // Abstract accessors
      for( int i=0; i<N; i++ )
        sb.ip("public Object f").p(i).p("() { return _f").p(i).p("; }").nl();
      // Abstract setters
      for( int i=0; i<N; i++ )
        sb.ip("public void f").p(i).p("(Object e) { _f").p(i).p("= (").p(clzs[i].box().toString()).p(")e; }").nl();
      // Class end
      sb.di().ip("}").nl();
      cache.put(tclz,sb.toString());
    }

    return xtt;
  }
}
