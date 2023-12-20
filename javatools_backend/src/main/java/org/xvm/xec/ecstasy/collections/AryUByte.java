package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.util.SB;

import org.xvm.xec.ecstasy.Iterablelong;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.collections.Ary.Mutability;

import java.lang.Iterable;
import java.util.Arrays;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class AryUByte extends XTC
  implements Iterable<Long>, Stringable
{
  public static final AryUByte GOLD = new AryUByte();
  
  public byte[] _es;
  public int _len;
  public AryUByte() { _es = new byte[1]; }
  public AryUByte(byte... es) { _es = es; _len=es.length; }
  public AryUByte(Mutability mutable, AryUByte es) { throw XEC.TODO(); }

  public boolean empty$get() { return _len==0; }
  
  // Fetch element; cannot specify an "unsigned" get at the java level
  public byte at(int idx) {
    if( 0 <= idx && idx < _len )
      return _es[idx];
    throw new ArrayIndexOutOfBoundsException(""+idx+" >= "+_len);
  }

  // Add an element, doubling base array as needed
  public AryUByte add( int e ) {
    if( _len >= _es.length ) {
      int len=1;
      while( len <= _es.length ) len<<=1;
      _es = Arrays.copyOf(_es,len);
    }
    _es[_len++] = (byte)e;
    return this;
  }

  public AryUByte addAll( AryUByte ls ) { throw XEC.TODO(); }

  public void removeUnordered(byte idx) { throw XEC.TODO(); }
  public void deleteUnordered(byte idx) { throw XEC.TODO(); }
  public void delete(byte idx) { throw XEC.TODO(); }

  
  private static final SB SBX = new SB();
  @Override public String toString() {
    SBX.p('[');
    for( int i=0; i<_len; i++ )
      SBX.p(_es[i]&0xFF).p(", ");
    String str = SBX.unchar(2).p(']').toString();
    SBX.clear();
    return str;
  }
  
  // Note that the hashCode() and equals() are not invariant to changes in the
  // underlying array.  If the hashCode() is used (e.g., inserting into a
  // HashMap) and the then the array changes, the hashCode() will change also.
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof AryUByte ary) ) return false;
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
    return (int)(sum ^ (sum>>32));
  }

     
  /** @return an iterator */
  @Override public Iterablelong iterator() { return new Iterablelong(0,_len); }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len*5; }
  @Override public Appenderchar appendTo(Appenderchar ary) {
    throw XEC.TODO();
  }

}
