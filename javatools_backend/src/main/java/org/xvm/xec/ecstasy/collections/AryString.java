package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.collections.Array.Mutability;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.Range;

import java.util.Arrays;
import java.util.function.LongFunction;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class AryString<A extends AryString> extends XTC
  implements Stringable
{
  public static final AryString GOLD = new AryString();
  
  public String[] _ss;
  public int _len;
  public AryString() { _ss = new String[1]; }
  public AryString(AryString as) { throw XEC.TODO(); }  
  public AryString(String... ss) { _ss = ss; _len = ss.length; }
  public AryString( long len, LongFunction<String> fcn ) {
    _len = (int)len;
    if( _len != len ) throw XEC.TODO(); // Too Big
    _ss = new String[_len];
    for( int i=0; i<_len; i++ )
      //_ss[i] = (char)fcn.applyAsLong(i);
      throw XEC.TODO();
  }
  public AryString(Mutability mutable, AryString as) { throw XEC.TODO(); }
  
  public boolean empty$get() { return _len==0; }

  // Add an element, doubling base array as needed
  public A add( String s ) {
    if( _len >= _ss.length ) _ss = Arrays.copyOf(_ss,Math.max(1,_ss.length<<1));
    _ss[_len++] = s;
    return (A)this;
  }

  // Fetch element
  public String at(long idx) {
    if( 0 <= idx && idx < _len )
      return _ss[(int)idx];
    throw new ArrayIndexOutOfBoundsException(""+idx+" >= "+_len);
  }

  /** Slice */
  public AryString at( Range r ) {
    throw XEC.TODO();
  }
  
  private static final SB SBX = new SB();
  @Override public String toString() {
    SBX.p('[');
    for( int i=0; i<_len; i++ )
      SBX.p('"').p(_ss[i]).p('"').p(", ");
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
    if( _ss == ary._ss ) return true;
    for( int i=0; i<_len; i++ )
      if( _ss[i] != ary._ss[i] )
        return false;
    return true;
  }
  @Override public int hashCode( ) {
    long sum=_len;
    for( int i=0; i<_len; i++ )
      sum ^= _ss[i].hashCode();
    return (int)sum;
  }

  AryString clear() { _len=0; return this; }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len; }
  @Override public Appenderchar appendTo(Appenderchar ary) {
    throw XEC.TODO();
  }
}
