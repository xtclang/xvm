package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.XEC;
import org.xvm.cc_explore.xclz.XClz;
import org.xvm.cc_explore.util.SB;
import java.lang.Iterable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.LongUnaryOperator;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class Arylong extends XClz implements Iterable<Long> {
  public long[] _es;
  public int _len;
  public Arylong() { _es = new long[1]; }
  public Arylong(long... es) { _es = es; _len=es.length; }
  public Arylong( long len, LongUnaryOperator fcn ) {
    _len = (int)len;
    if( _len != len ) throw XEC.TODO(); // Too Big
    _es = new long[_len];
    for( int i=0; i<_len; i++ )
      _es[i] = fcn.applyAsLong(i);
  }
  

  // Add an element, doubling base array as needed
  public Arylong add( long e ) {
    if( _len >= _es.length ) {
      int len=1;
      while( len <= _es.length ) len<<=1;
      _es = Arrays.copyOf(_es,len);
    }
    _es[_len++] = e;
    return this;
  }

  // Fetch element
  public long at(long idx) {
    if( 0 <= idx && idx < _len )
      return _es[(int)idx];
    throw new ArrayIndexOutOfBoundsException(""+idx+" >= "+_len);
  }

  private static final SB SBX = new SB();
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
    return (int)sum;
  }

     
  /** @return an iterator */
  @Override public XIter64 iterator() { return new XIter64(0,_len); }

}
