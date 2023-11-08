package org.xvm.xrun;

import org.xvm.XEC;
import org.xvm.xclz.XClz;
import org.xvm.util.SB;
import java.util.Arrays;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class Arychar extends XClz implements XStringable {
  public char[] _cs;
  public int _len;
  public Arychar() { _cs = new char[1]; }
  public Arychar(String s) { _cs = s.toCharArray(); _len = _cs.length; }  

  // Add an element, doubling base array as needed
  public Arychar add( char c ) {
    if( _len >= _cs.length ) _cs = Arrays.copyOf(_cs,Math.max(1,_cs.length<<1));
    _cs[_len++] = c;
    return this;
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

  public Arychar appendTo(String str) {
    throw XEC.TODO();
  }

  
  // --- XStringable
  @Override public int estimateStringLength() { return _len; }
  @Override public Arychar appendTo(Arychar ary) {
    throw XEC.TODO();
  }
}
