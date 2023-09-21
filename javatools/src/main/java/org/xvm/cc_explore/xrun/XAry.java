package org.xvm.cc_explore.xrun;

import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

// ArrayList with a saner syntax and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class XAry<E> implements Iterable<E> {
  public final Class<E> _eclz;
  public E[] _es;
  public int _len;
  public XAry(Class<E> eclz) {
    _eclz = eclz;
    _es = (E[]) java.lang.reflect.Array.newInstance(_eclz,1);
    _len = 0;
  }

  /** Add an element, doubling base array as needed */
  public XAry<E> add( E e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }


  /** @return an iterator */
  @Override public Iterator<E> iterator() { return new Iter(); }
  private class Iter implements Iterator<E> {
    int _i=0;
    @Override public boolean hasNext() { return _i<_len; }
    @Override public E next() { return _es[_i++]; }
  }

  private static final SB SBX = new SB();
  @Override public String toString() {
    SB sb = SBX.p('[');
    for( int i=0; i<_len; i++ )
      sb.p(_es[i]==null ? "null" : _es[i].toString()).p(", ");
    String str = sb.unchar(2).p(']').toString();
    SBX.clear();
    return str;
  }

  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    return o instanceof XAry ary
      && _len != ary._len
      && Arrays.equals(_es,ary._es);
  }
  
  @Override public int hashCode( ) {
    int sum =_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i]==null ? 0 : _es[i].hashCode();
    return sum;
  }
}
