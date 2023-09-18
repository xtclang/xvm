package org.xvm.cc_explore.xrun;

import java.util.Arrays;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class XAryI64 {
  public long[] _es;
  public int _len;
  public XAryI64() { _es = new long[1]; }


  /** Add an element, doubling base array as needed */
  public void add( long e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
  }
  

  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof XAryI64 ary) ) return false;
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
}
