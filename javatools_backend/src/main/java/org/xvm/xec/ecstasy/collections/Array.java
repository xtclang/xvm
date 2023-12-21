package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Enum;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.Range;
import org.xvm.xrun.Never;

import java.lang.Iterable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.LongFunction;

// ArrayList with a saner syntax and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class Array<E> extends XTC implements Iterable<E>, Stringable {
  public static final Array GOLD = new Array();
  public Array(Never n) { }       // No arg constructor

  public E[] _es;
  public int _len;
  
  @SuppressWarnings("unchecked")
  public Array(Class<E> clazz) { this((E[]) java.lang.reflect.Array.newInstance(clazz, 1),0); }
  public Array( E... es ) { this(es,es.length); }
  public Array( E[] es, int len ) {
    _es = es;
    _len = len;
  }
  public Array(Class<E> clazz, int len, LongFunction<E> fcn ) {
    _es = (E[])java.lang.reflect.Array.newInstance(clazz, len);
    _len = len;
    for( int i=0; i<len; i++ )
      _es[i] = fcn.apply(i);
  }
  public Array(Class<E> clazz, Mutability mut, Array<E> as ) {
    throw XEC.TODO();
  }

  /** Empty, as encoded as a size property read */
  public boolean empty$get() { return _len==0; }
  
  /** Length, as encoded as a size property read */
  public int size$get() { return _len; }
  
  /** Element at */
  public E at( long idx ) {
    if( idx >= _len ) throw new ArrayIndexOutOfBoundsException((int)idx);
    return _es[(int)idx];
  }
  
  /** Add an element, doubling base array as needed */
  public Array<E> add( E e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }

  /** Slice */
  public Array<E> at( Range r ) {
    throw XEC.TODO();
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
    return o instanceof Array ary
      && _len != ary._len
      && Arrays.equals(_es,ary._es);
  }
  
  @Override public int hashCode( ) {
    int sum =_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i]==null ? 0 : _es[i].hashCode();
    return sum;
  }


  // --- Mutability
  public enum Mutability {
    Constant,                   // Deeply immutable
    Persistent,                 // Odd name, but shallow immutable
    Fixed,                      // Tuples and arrays are fixed length, but mutable
    Mutable;                    // Classic mutable    
    public static final Mutability[] VALUES = values();
    public static final Enum GOLD = Enum.GOLD; // Dispatch against Ordered class same as Enum class
  }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len*10; }
  @Override public Appenderchar appendTo(Appenderchar buf) {
    return buf.appendTo(toString());
  }
}
