package org.xvm.xec.ecstasy.collections;

import java.util.Arrays;
import java.util.Random;
import java.util.function.LongUnaryOperator;
import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xrun.XRuntime;
import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
@SuppressWarnings("unused")
public class Arylong extends Array<Int64> {
  public static final Arylong GOLD = new Arylong();
  public static final Arylong EMPTY= new Arylong();

  public long[] _es;
  private Arylong(Mutability mut, long[] es) { super(Int64.GOLD,mut,es.length); _es = es; }
  public  Arylong(                    ) { this(Mutable , new long[ 0 ]); }
  public  Arylong(int len             ) { this(Fixed   , new long[len]); }
  public  Arylong(double x, long... es) { this(Constant, es); }
  public  Arylong(Mutability mut, Arylong as) { this(mut,as._es.clone()); }
  public  Arylong(Arylong as) { this(as._mut,as); }

  public Arylong( long len, LongUnaryOperator fcn ) {
    this((int)len);
    if( _len != len ) throw XEC.TODO(); // Too Big
    for( int i=0; i<_len; i++ )
      _es[i] = fcn.applyAsLong(i);
  }
  public static Arylong construct(Mutability mut, Arylong as) { return new Arylong(mut,as); }
  public static Arylong construct() { return new Arylong(); }
  public static Arylong construct( long len, LongUnaryOperator fcn ) { return new Arylong(len,fcn); }
  public static Arylong construct(long len) { return new Arylong((int)len); }


  // Fetch element
  public long at8(long idx) {
    if( 0 <= idx && idx < _len )
      return _es[(int)idx];
    throw new ArrayIndexOutOfBoundsException( idx+" >= "+_len );
  }
  @Override public Int64 at(long idx) { return Int64.make(at8(idx)); }

  // Add an element, doubling base array as needed
  public Arylong add( long e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }

  // Add an element, doubling base array as needed
  public Arylong add( Int64 e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e._i;         // Unbox
    return this;
  }

  /** Slice */
  public Arylong slice( AbstractRange r ) {
    throw XEC.TODO();
  }

  public Arylong addAll( Arylong ls ) {
    for( int i=0; i<ls._len; i++ )
      add(ls._es[i]);
    return this;
  }

  public int indexOf( long e ) {
    for( int i=0; i<_len; i++ )
      if( _es[i]==e )
        return XRuntime.SET$COND(true,i);
    return XRuntime.SET$COND(false,-1);
  }
  public Arylong removeUnordered(long e) {
    int idx = indexOf(e);
    return idx== -1 ? this : deleteUnordered(idx);
  }
  public Arylong deleteUnordered(long idx) {
    _es[(int)idx] = _es[--_len];
    return this;
  }

  public Arylong delete(long idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }

  public Arylong shuffled(boolean inPlace) {
    if( inPlace )
      throw XEC.TODO();
    // Not inPlace so clone
    Arylong ary = new Arylong(_mut,this);
    Random R = new Random();
    for( int i=0; i<_len; i++ ) {
      int idx = R.nextInt(_len);
      long x = ary._es[i];  ary._es[i] = ary._es[idx];  ary._es[idx] = x;
    }
    return ary;
  }

  /** @return an iterator */
  public Iterlong iterator() { return new Iterlong(); }
  public class Iterlong extends Iterator<Int64> {
    private int _i;
    @Override public Int64 next() { return Int64.make(next8()); }
    @Override public long next8() { return XRuntime.SET$COND(hasNext(), _es[_i++]); }
    @Override public boolean hasNext() { return _i<_len; }
    @Override public final String toString() { return _i+".."+_len; }
    // --- Comparable
    @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }
  }

  // --- Freezable
  @Override public Arylong freeze(boolean inPlace) {
    if( _mut==Constant ) return this;
    if( !inPlace ) return construct(Constant,this);
    _mut = Constant;
    return this;
  }

  // --- Appender
  @Override public Arylong appendTo( Int64 s ) { return add(s); }

  @Override public String toString() {
    SBX.p("[  ");
    for( int i=0; i<_len; i++ )
      SBX.p(_es[i]).p(", ");
    String str = SBX.unchar(2).p("]").toString();
    SBX.clear();
    return str;
  }

  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof Arylong ary) ) return false;
    if( _len != ary._len ) return false;
    if( _es == ary._es ) return true;
    for( int i=0; i<_len; i++ )
      if( _es[i] != ary._es[i] )
        return false;
    return true;
  }
  @Override public int hashCode( ) {
    long sum=_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i];
    return (int)(sum ^ (sum>>32));
  }

}
