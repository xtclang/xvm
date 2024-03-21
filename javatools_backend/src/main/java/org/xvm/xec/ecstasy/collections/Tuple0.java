package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.util.SB;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xec.ecstasy.text.Char;
import org.xvm.xec.ecstasy.collections.Array.Mutability;

public class Tuple0 extends XTC implements Tuple {
  public static final Tuple0 GOLD = new Tuple0();
  byte _mut = -1;
  final short _len; // 0-32767 length limit

  public Tuple0() { this(0); }
  public Tuple0(int n) { _len = (short)n; }
  @Override public XTC at(long i) { throw XEC.TODO(); }
  @Override public void set(long i, XTC e) { throw XEC.TODO(); }

  public int size$get() { return _len; }


  // Select a range using array syntax.
  // Loses all Java compiler knowledge of the types.
  @Override public Tuple at( AbstractRange r) {
    if( r._incr != 1 ) throw XEC.TODO();
    XTC[] es = new XTC[(int)r.span()];
    for( int i=0; i<es.length; i++ )
      es[i] = at((int)(i+r._start));
    TupleN t = new TupleN(es);
    t._mut = _mut;              // Same mutability
    return t;
  }

  @Override
  public Mutability mutability$get() {
    if( _mut == -1 ) {          // Not cached?  Compute once
      for( int i=0; i<_len; i++ ) {
        XTC o = at(i);
        //// These things are all Constant mutability
        //if( o instanceof Number ) continue; // ints, longs, doubles
        //if( o instanceof String ) continue;
        //if( o instanceof Character ) continue;
        throw XEC.TODO();
      }
      _mut = (byte)Mutability.Constant.ordinal();
    }
    return Mutability.VALUES[_mut];
  }

  // Converts the underlying Tuple to a TupleN with the extra field.
  // Loses all Java compiler knowledge of the types.
  @Override
  public Tuple add( XTC x) {
    // TODO: if the underlying class is already a TupleN we can use
    // Arrays.copyOf instead of a manual copy.
    XTC[] es = new XTC[_len+1];
    for( int i=0; i<_len; i++ )
      es[i] = at(i);
    es[_len] = x;
    return new TupleN(es);
  }

  // Loses all Java compiler knowledge of the types.
  @Override
  public Tuple addAll( Tuple tup ) {
    int len = tup.size$get();
    XTC[] es = new XTC[_len + len];
    for( int i=0; i<_len; i++ )
      es[i] = at(i);
    for( int i=0; i<len; i++ )
      es[i+_len] = tup.at(i);
    // TODO: Complex default mutability
    return new TupleN(es);
  }

  // Extends the tuple, loses all type knowledge
  public  TupleN  add( XTC type, char   x ) { return _add(type,Char .make(x)); }
  public  TupleN  add( XTC type, long   x ) { return _add(type,Int64.make(x)); }
  public  TupleN  add( XTC type, String x ) { return _add(type,org.xvm.xec.ecstasy.text.String.make(x)); }
  public  TupleN  add( XTC type, XTC x ) { return _add(type,x); }
  private TupleN _add( XTC type, XTC x ) {
    int len = size$get();
    XTC[] es = new XTC[len + 1];
    for( int i=0; i<_len; i++ )
      es[i] = at(i);
    es[len] = x;
    return new TupleN(es);
  }


  // Crazy contract... copied from XTC Tuple.x comments
  public Tuple ensureMutability(Mutability mut0, boolean inPlace) {
    Mutability mut = mutability$get();
    if( mut==mut0 ) return this;
    if( mut==Mutability.Constant && mut0==Mutability.Persistent ) return this;
    if( mut==Mutability.Fixed && mut0==Mutability.Persistent && inPlace ) throw XEC.TODO(); // Update self to Persistent
    if( mut0==Mutability.Constant ) throw XEC.TODO(); // Deep freeze in place
    // Copy, then set mutability
    try {
      Tuple0 clone = (Tuple0)clone();
      clone._mut = (byte)mut0.ordinal();
      return clone;
    } catch( CloneNotSupportedException cnse ) {throw new RuntimeException(cnse); }
  }


  @Override public boolean equals( XTC x0, XTC x1 ) {
    Tuple0 t0 = (Tuple0)x0;     // Contract
    Tuple0 t1 = (Tuple0)x1;     // Contract
    if( t0._len != t1._len ) return false;
    for( int i=0; i<t0._len; i++ )
      if( !t0.at(i).equals(t1.at(i)) )
        return false;
    return true;
  }

  @Override public String toString() {
    SB sb = new SB().p("( ");
    for( int i=0; i<_len; i++ )
      sb.p(at(i)==null ? "null" : at(i).toString()).p(", ");
    return sb.unchar(2).p(")").toString();
  }
}
