package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Enum;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.Range;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xrun.Never;
import org.xvm.xrun.XRuntime;

import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

import java.util.Arrays;
import java.util.function.LongFunction;

// ArrayList with a saner syntax and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class AryXTC<E extends XTC> extends Array<E> {
  public static final AryXTC GOLD = new AryXTC((Never)null);
  public AryXTC(Never n) { super(n); } // No-arg-no-work constructor
  public static final AryXTC EMPTY = new AryXTC(null,new XTC[0]);

  public E[] _es;
  private AryXTC(E gold, Mutability mut, E[] es) { super(gold,mut,es.length); _es = es; }
  public  AryXTC(E gold         ) { this(gold , Mutable , EMPTY); }
  public  AryXTC(E gold, int len) { this(gold , Fixed   , (E[]) java.lang.reflect.Array.newInstance(gold.getClass(), len)); }  
  public  AryXTC(E gold, E[] es ) { this(gold , Constant, es); }
  public  AryXTC(E... es        ) { this(es[0], Constant, Arrays.copyOfRange(es,1,es.length)); }
  public  AryXTC(E gold, Mutability mut, AryXTC<E> as) { this(gold,mut,as._es.clone()); }
  public  AryXTC(AryXTC<E> as) { this(as._gold,as._mut,as._es.clone()); }

  public static <E extends XTC> AryXTC<E> construct(E gold) { return new AryXTC<>(gold); }

  
  public AryXTC(E gold, long len, LongFunction<E> fcn ) {
    this(gold,(int)len);
    if( _len != len ) throw XEC.TODO(); // Too Big
    for( int i=0; i<_len; i++ )
      _es[i] = fcn.apply(i);
  }
  
  /** Element at */
  public E at( long idx ) { return at8(idx); }
  public E at8( long idx ) {
    if( idx >= _len ) throw new ArrayIndexOutOfBoundsException((int)idx);
    return _es[(int)idx];
  }
  
  /** Add an element, doubling base array as needed */
  public AryXTC<E> add( E e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e;
    return this;
  }

  /** Slice */
  public AryXTC<E> at( Range r ) {
    throw XEC.TODO();
  }
  public AryXTC delete(long idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }
  
  /** @return an iterator */
  @Override public Iterator<E> iterator() { return new Iter(); }  
  private class Iter extends Iterator<E> {
    private int _i;
    @Override public E next() { return XRuntime.SET$COND(hasNext(), _es[_i++]); }
    @Override public boolean hasNext() { return _i<_len; }  
    @Override public final String toString() { return ""+_i+".."+_len; }
    // --- Comparable
    @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }  
  }

  @Override public String toString() {
    SB sb = SBX.p('[');
    for( int i=0; i<_len; i++ )
      sb.p(_es[i]==null ? "null" : _es[i].toString()).p(", ");
    String str = sb.unchar(2).p(']').toString();
    SBX.clear();
    return str;
  }

  public static <E extends XTC> boolean equals$AryXTC( AryXTC gold, Array<E> a0, Array<E> a1 ) {
    if( a0 == a1 ) return true;
    if( a0._len != a1._len ) return false;
    for( int i=0; i<a0._len; i++ )
      if( !gold._gold.equals(a0.at(i),a1.at(i)) )
        return false;
    return true;
  }
  
  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    return o instanceof AryXTC ary && equals$AryXTC(null,this,ary);
  }
  
  @Override public int hashCode( ) {
    int sum =_len;
    for( int i=0; i<_len; i++ )
      sum += _es[i]==null ? 0 : _es[i].hashCode();
    return sum;
  }

}
