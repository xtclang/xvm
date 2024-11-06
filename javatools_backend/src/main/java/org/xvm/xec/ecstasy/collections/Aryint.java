package org.xvm.xec.ecstasy.collections;

import java.util.Arrays;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xec.ecstasy.Boolean;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.numbers.Int32;
import org.xvm.xrun.XRuntime;
import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
@SuppressWarnings("unused")
public class Aryint extends Array<Int32> {
  public static final Aryint GOLD = new Aryint();
  public static final Aryint EMPTY= new Aryint();

  public int[] _es;
  private Aryint(Mutability mut, int[] es) { super(Int32.GOLD,mut,es.length); _es = es; }
  public  Aryint(                    ) { this(Mutable , new int[ 0 ]); }
  public  Aryint(int len             ) { this(Fixed   , new int[len]); }
  public  Aryint(double x, int... es)  { this(Constant, es); }
  public  Aryint(Mutability mut, Aryint as) { this(mut,as._es.clone()); }
  public  Aryint(Aryint as) { this(as._mut,as); }
  public  Aryint(double x, long... es) {
    super(Int32.GOLD,Constant,es.length);
    _es = new int[es.length];
    for( int i=0; i<es.length; i++ )
      _es[i] = (int)es[i];
  }

  public Aryint( int len, IntUnaryOperator fcn ) {
    this((int)len);
    if( _len != len ) throw XEC.TODO(); // Too Big
    for( int i=0; i<_len; i++ )
      _es[i] = fcn.applyAsInt(i);
  }
  public static Aryint construct(Mutability mut, Aryint as) { return new Aryint(mut,as); }
  public static Aryint construct() { return new Aryint(); }
  public static Aryint construct( int len, IntUnaryOperator fcn ) { return new Aryint(len,fcn); }
  public static Aryint construct(int len) { return new Aryint((int)len); }


  // Fetch element
  public int at8(long idx) {
    if( 0 <= idx && idx < _len )
      return _es[(int)idx];
    throw new ArrayIndexOutOfBoundsException( idx+" >= "+_len );
  }
  @Override public Int32 at(long idx) { return Int32.make(at8(idx)); }

  // Add an element, doubling base array as needed
  public Aryint add( int e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }

  // Add an element, doubling base array as needed
  public Aryint add( Int32 e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e._i;         // Unbox
    return this;
  }

  /** Slice */
  public Aryint slice( AbstractRange r ) {
    throw XEC.TODO();
  }

  public Aryint addAll( Aryint ls ) {
    for( int i=0; i<ls._len; i++ )
      add(ls._es[i]);
    return this;
  }

  public int indexOf( int e ) {
    for( int i=0; i<_len; i++ )
      if( _es[i]==e )
        return XRuntime.True(i);
    return XRuntime.False(-1);
  }
  public Aryint removeUnordered(int e) {
    int idx = indexOf(e);
    return idx== -1 ? this : deleteUnordered(idx);
  }
  public Aryint deleteUnordered(int idx) {
    _es[(int)idx] = _es[--_len];
    return this;
  }

  public Aryint delete(int idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }

  public Aryint shuffled(Boolean inPlace) { return shuffled(inPlace._i); }
  public Aryint shuffled(boolean inPlace) {
    if( inPlace )
      throw XEC.TODO();
    // Not inPlace so clone
    Aryint ary = new Aryint(_mut,this);
    Random R = new Random();
    for( int i=0; i<_len; i++ ) {
      int idx = R.nextInt(_len);
      int x = ary._es[i];  ary._es[i] = ary._es[idx];  ary._es[idx] = x;
    }
    return ary;
  }

  /** @return an iterator */
  public Iterint iterator() { return new Iterint(); }
  public class Iterint extends Iterator<Int32> {
    private int _i;
    @Override public Int32 next() { return Int32.make(next8()); }
    @Override public long next8() { return (XRuntime.$COND = hasNext()) ? _es[_i++] : 0; }
    @Override public boolean hasNext() { return _i<_len; }
    @Override public final String toString() { return _i+".."+_len; }
    // --- Comparable
    @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }
  }

  // --- Freezable
  @Override public Aryint freeze(boolean inPlace) {
    if( _mut==Constant ) return this;
    if( !inPlace ) return construct(Constant,this);
    _mut = Constant;
    return this;
  }

  // --- Appender
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
    if( !(o instanceof Aryint ary) ) return false;
    if( _len != ary._len ) return false;
    if( _es == ary._es ) return true;
    for( int i=0; i<_len; i++ )
      if( _es[i] != ary._es[i] )
        return false;
    return true;
  }
  @Override public int hashCode( ) {
    int sum=_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i];
    return (int)(sum ^ (sum>>32));
  }

}
