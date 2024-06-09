package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.util.SB;
import org.xvm.xec.ecstasy.Boolean;
import org.xvm.xec.ecstasy.*;
import org.xvm.xrun.XRuntime;

import java.util.Arrays;

import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
@SuppressWarnings("unused")
public class Aryboolean extends Array<Boolean> {
  public static final Aryboolean GOLD = new Aryboolean();
  public static final Aryboolean EMPTY= new Aryboolean();

  public boolean[] _es;
  private Aryboolean(Mutability mut, boolean[] es) { super(Boolean.GOLD,mut,es.length); _es = es; }
  public  Aryboolean(                       ) { this(Mutable , new boolean[ 0 ]); }
  public  Aryboolean(long len               ) { this(Fixed   , new boolean[(int)len]); }
  public  Aryboolean(double x, boolean... es) { this(Constant, es); }
  public  Aryboolean(Mutability mut, Aryboolean as) { this(mut,as._es.clone()); }
  public  Aryboolean(Aryboolean as) { this(as._mut,as); }

  public interface IntBooleanOper { boolean apply(int i); }
  public Aryboolean( long len, IntBooleanOper fcn ) {
    this((int)len);
    if( _len != len ) throw XEC.TODO(); // Too Big
    for( int i=0; i<_len; i++ )
      _es[i] = fcn.apply(i);
  }

  public static Aryboolean construct(Mutability mut, Aryboolean as) { return new Aryboolean(mut,as); }
  public static Aryboolean construct(long len) { return new Aryboolean(len); }
  public static Aryboolean construct( long len, IntBooleanOper fcn ) { return new Aryboolean(len,fcn); }

  // Fetch element
  public boolean at8(long idx) {
    if( 0 <= idx && idx < _len )
      return _es[(int)idx];
    throw new ArrayIndexOutOfBoundsException( idx+" >= "+_len );
  }
  public boolean getElement(long idx ) { return at8(idx); }

  // Fetch element
  @Override public Boolean at(long idx) { return Boolean.make(at8(idx)); }

  // Add an element, doubling base array as needed
  public Aryboolean add( boolean c ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = c;
    return this;
  }
  // Add an element, doubling base array as needed
  @Override public Aryboolean add( Boolean c ) { return add(c==Boolean.TRUE); }

  public void set(long idx, boolean e) {
    if( !(0 <= idx && idx < _len) )
      throw new ArrayIndexOutOfBoundsException( idx+" >= "+_len );
    _es[(int)idx] = e;
  }

  public void setElement(long idx, boolean e) { set(idx,e); }

  /** Slice */
  public Aryboolean slice( AbstractRange r ) { throw XEC.TODO(); }

  public Aryboolean delete(long idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }

  @Override public Aryboolean toArray(Mutability mut, boolean inPlace) {
    return (Aryboolean)super.toArray(mut,inPlace);
  }

  @Override public String toString() {
    SBX.p('[');
    for( int i=0; i<_len; i++ )
      SBX.p(_es[i]).p(", ");
    String str = SBX.unchar(2).p(']').toString();
    SBX.clear();
    return str;
  }

  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( java.lang.Object o ) {
    if( this==o ) return true;
    if( !(o instanceof Aryboolean ary) ) return false;
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
      sum = (sum<<1) | (sum>>>63) ^ (_es[i] ? 1 : 0);
    return (int)sum;
  }

  Aryboolean clear() { _len=0; return this; }

  /** @return an iterator */
  @Override public Iterboolean iterator() { return new Iterboolean(); }
  public class Iterboolean extends Iterator<Boolean> {
    private int _i;
    @Override public Boolean next() { return (XRuntime.$COND = hasNext()) ? Boolean.make(_es[_i++]) : Boolean.FALSE; }
    @Override public boolean hasNext() { return _i<_len; }
    @Override public final String toString() { return _i+".."+_len; }
    // --- Comparable
    @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }
  }


  // --- Freezable
  @Override public Aryboolean freeze(boolean inPlace) {
    if( _mut==Mutability.Constant ) return this;
    if( !inPlace ) return construct(Mutability.Constant,this);
    _mut = Mutability.Constant;
    return this;
  }

  // --- Appender

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len; }
}
