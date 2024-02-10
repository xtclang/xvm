package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.Range;
import org.xvm.xec.ecstasy.collections.Array.Mutability;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xrun.XRuntime;

import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

import java.util.Arrays;
import java.util.function.LongFunction;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class AryString extends Array<org.xvm.xec.ecstasy.text.String> {
  public static final AryString GOLD = new AryString();
  public static final AryString EMPTY= new AryString();
  
  public String[] _es;
  private AryString(Mutability mut, String[] es) { super(org.xvm.xec.ecstasy.text.String.GOLD,mut,es.length); _es = es; }
  public  AryString(                      ) { this(Mutable , new String[ 0 ]); }
  private AryString(int len               ) { this(Fixed   , new String[len]); }
  public  AryString(double x, String... es) { this(Constant, es); }
  public  AryString(Mutability mut, AryString as) { this(mut,as._es.clone()); }
  public  AryString(AryString as) { this(as._mut,as._es.clone()); }
  
  public AryString( long len, LongFunction<String> fcn ) {
    this((int)len);
    if( _len != len ) throw XEC.TODO(); // Too Big
    for( int i=0; i<_len; i++ )
      _es[i] = fcn.apply(i);
  }

  public static AryString construct() { return new AryString(); }
  
  // Add an element, doubling base array as needed
  public AryString add( org.xvm.xec.ecstasy.text.String s ) { return add(s._i); }
  // Add an element, doubling base array as needed
  public AryString add( String s ) { 
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = s;
    return this;
  }

  // Fetch element
  public String at8(long idx) {
    if( 0 <= idx && idx < _len )
      return _es[(int)idx];
    throw new ArrayIndexOutOfBoundsException(""+idx+" >= "+_len);
  }
  
  @Override public org.xvm.xec.ecstasy.text.String at(long idx) {
    return org.xvm.xec.ecstasy.text.String.make(at8(idx));
  }

  /** Slice */
  public AryString at( Range r ) {
    if( r._invert ) throw XEC.TODO();
    return new AryString(_mut, Arrays.copyOfRange(_es,(int)r._lo,(int)r._hi));
  }

  public int indexOf( String e ) {
    for( int i=0; i<_len; i++ )
      if( _es[i].equals(e) )
        return XRuntime.SET$COND(true,i);
    return XRuntime.SET$COND(false,-1);
  }
  public AryString removeUnordered(String e) {
    int idx = indexOf(e);
    return idx== -1 ? this : deleteUnordered(idx);
  }
  public AryString deleteUnordered(int idx) {
    _es[idx] = _es[--_len];
    return this;
  }
  
  public AryString delete(long idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }

  public AryString freeze( boolean inPlace ) { throw XEC.TODO(); }
 
  /** @return an iterator */
  @Override public Iterator<org.xvm.xec.ecstasy.text.String> iterator() { return new IterString(); }
  public class IterString extends Iterator<org.xvm.xec.ecstasy.text.String> {
    private int _i;
    @Override public org.xvm.xec.ecstasy.text.String next() { return org.xvm.xec.ecstasy.text.String.make(next8()); }
    public String next8() { return XRuntime.SET$COND(hasNext(), _es[_i++]); }
    @Override public boolean hasNext() { return _i<_len; }  
    @Override public final String toString() { return ""+_i+".."+_len; }
    // --- Comparable
    public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }  
  }

  private static final SB SBX = new SB();
  @Override public String toString() {
    SBX.p('[');
    for( int i=0; i<_len; i++ )
      SBX.p('"').p(_es[i]).p('"').p(", ");
    String str = SBX.unchar(2).p(']').toString();
    SBX.clear();
    return str;
  }
  
  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof AryString ary) ) return false;
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
      sum ^= _es[i].hashCode();
    return (int)sum;
  }

  AryString clear() { _len=0; return this; }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len; }
  @Override public Appenderchar appendTo(Appenderchar ary) {
    throw XEC.TODO();
  }
}
