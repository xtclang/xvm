package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.Appenderchar;

import java.util.Arrays;
import java.util.function.LongUnaryOperator;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class Arychar<A extends Arychar> extends XTC
  implements Stringable
{
  public static final Arychar GOLD = new Arychar();
  
  public char[] _cs;
  public int _len;
  public Arychar() { _cs = new char[1]; }
  public Arychar(String s) { _cs = s.toCharArray(); _len = _cs.length; }  
  public Arychar( long len, LongUnaryOperator fcn ) {
    _len = (int)len;
    if( _len != len ) throw XEC.TODO(); // Too Big
    _cs = new char[_len];
    for( int i=0; i<_len; i++ )
      //_cs[i] = (char)fcn.applyAsLong(i);
      throw XEC.TODO();
  }
  
  // Add an element, doubling base array as needed
  public A add( char c ) {
    if( _len >= _cs.length ) _cs = Arrays.copyOf(_cs,Math.max(1,_cs.length<<1));
    _cs[_len++] = c;
    return (A)this;
  }

  // Fetch element
  public char at(long idx) {
    if( 0 <= idx && idx < _len )
      return _cs[(int)idx];
    throw new ArrayIndexOutOfBoundsException(""+idx+" >= "+_len);
  }

  private static final SB SBX = new SB();
  @Override public String toString() {
    SBX.p('[');
    for( int i=0; i<_len; i++ )
      SBX.p(_cs[i]).p(", ");
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
    if( _cs == ary._cs ) return true;
    for( int i=0; i<_len; i++ )
      if( _cs[i] != ary._cs[i] )
        return false;
    return true;
  }
  @Override public int hashCode( ) {
    long sum=_len;
    for( int i=0; i<_len; i++ )
      sum += _cs[i];
    return (int)sum;
  }

  Arychar clear() { _len=0; return this; }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len; }
  @Override public Appenderchar appendTo(Appenderchar ary) {
    throw XEC.TODO();
  }
}
