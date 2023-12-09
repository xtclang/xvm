package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Enum;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xrun.Never;

import java.lang.Iterable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;

// ArrayList with a saner syntax and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class Ary<E> extends XTC implements Iterable<E>, Stringable {
  public static final Ary GOLD = new Ary();
  public Ary(Never n) { }       // No arg constructor

  public E[] _es;
  public int _len;
  
  @SuppressWarnings("unchecked")
  public Ary(Class<E> clazz) { this((E[]) Array.newInstance(clazz, 1),0); }
  public Ary( E... es ) { this(es,es.length); }
  public Ary( E[] es, int len ) {
    _es = es;
    _len = len;
  }

  /** Length, as encoded as a size property read */
  public int size$get() { return _len; }
  
  /** Element at */
  public E at( int idx ) {
    if( idx >= _len ) throw new ArrayIndexOutOfBoundsException(idx);
    return _es[idx];
  }
  
  /** Add an element, doubling base array as needed */
  public Ary<E> add( E e ) {
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
    return o instanceof Ary ary
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
