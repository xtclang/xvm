package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.Range;
import org.xvm.xec.ecstasy.text.Char;
import org.xvm.xrun.XRuntime;

import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

import java.util.Arrays;
import java.util.function.LongUnaryOperator;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
@SuppressWarnings("unused")
public class Arychar extends Array<Char> {
  public static final Arychar GOLD = new Arychar();
  public static final Arychar EMPTY= new Arychar();
  
  public char[] _es;
  private Arychar(Mutability mut, char[] es) { super(Char.GOLD,mut,es.length); _es = es; }
  public  Arychar(                    ) { this(Mutable , new char[ 0 ]); }
  public  Arychar(int len             ) { this(Fixed   , new char[len]); }
  public  Arychar(double x, char... es) { this(Constant, es); }
  public  Arychar(Mutability mut, Arychar as) { this(mut    , as._es.clone()); }
  public  Arychar(Arychar as) { this(as._mut,as); }
  
  public Arychar( long len, LongUnaryOperator fcn ) {
    this((int)len);
    if( _len != len ) throw XEC.TODO(); // Too Big
    for( int i=0; i<_len; i++ )
      _es[i] = (char)fcn.applyAsLong(i);
  }
  
  public Arychar(String s) {
    this(s.length(), i -> s.charAt((int)i));
  }
  
  // Fetch element
  public char at8(long idx) {
    if( 0 <= idx && idx < _len )
      return _es[(int)idx];
    throw new ArrayIndexOutOfBoundsException( idx+" >= "+_len );
  }
  // Fetch element
  @Override public Char at(long idx) { return Char.make(at8(idx)); }

  // Add an element, doubling base array as needed
  public Arychar add( char c ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = c;
    return this;
  }
  // Add an element, doubling base array as needed
  @Override public Arychar add( Char c ) { return add(c._c); }

  public Arychar delete(long idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }

  /** Slice */
  public Arychar at( Range r ) {
    throw XEC.TODO();
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
    if( !(o instanceof Arychar ary) ) return false;
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

  Arychar clear() { _len=0; return this; }

  /** @return an iterator */
  @Override public Iterchar iterator() { return new Iterchar(); }
  public class Iterchar extends Iterator<Char> {
    private int _i;
    @Override public Char next() { return Char.make(next8()); }
    public char next8() { return XRuntime.SET$COND(hasNext(), _es[_i++]); }
    @Override public boolean hasNext() { return _i<_len; }  
    @Override public final String toString() { return _i+".."+_len; }
    @Override public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }  
  }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len; }
  @Override public Appenderchar appendTo(Appenderchar ary) {
    throw XEC.TODO();
  }
}
