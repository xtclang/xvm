package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xec.ecstasy.numbers.UInt8;

import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

import java.util.Arrays;

// ArrayList with primitives and an exposed API for direct use by code-gen.
// Not intended for hand use.
public class AryUInt8 extends Array<UInt8> {
  public static final AryUInt8 GOLD = new AryUInt8();

  public byte[] _es;
  public  AryUInt8(Mutability mut, byte[] es) { super(UInt8.GOLD,mut,es.length); _es = es; }
  public  AryUInt8(                    ) { this(Mutable , new byte[ 0 ]); }
  public  AryUInt8(int len             ) { this(Fixed   , new byte[len]); }
  public  AryUInt8(double x, byte... es) { this(Constant, es); }
  public  AryUInt8(Mutability mut, AryUInt8 as) { this(mut,as._es.clone()); }
  public  AryUInt8(AryUInt8 as) { this(as._mut,as._es.clone()); }

  public static AryUInt8 construct() { return new AryUInt8(); }
  public static AryUInt8 construct(Mutability mut, AryUInt8 as) { return new AryUInt8(mut,as); }

  // Fetch element; cannot specify an "unsigned" get at the java level
  public int at8(long idx) {
    if( 0 <= idx && idx < _len )
      return (_es[(int)idx]&0xFF);
    throw new ArrayIndexOutOfBoundsException(""+idx+" >= "+_len);
  }
  @Override public UInt8 at(long idx) { return UInt8.make((byte)at8(idx)); }

  // Add an element, doubling base array as needed
  public AryUInt8 add( int e ) {
    if( _len >= _es.length ) {
      int len=1;
      while( len <= _es.length ) len<<=1;
      _es = Arrays.copyOf(_es,len);
    }
    _es[_len++] = (byte)e;
    return this;
  }

  // Add an element, doubling base array as needed
  public AryUInt8 add( UInt8 e ) {
    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    _es[_len++] = e._i;         // Unbox
    return this;
  }

  /** Slice */
  public AryUInt8 slice( AbstractRange r ) { throw XEC.TODO(); }

  public AryUInt8 addAll( AryUInt8 ls ) { throw XEC.TODO(); }

  public void removeUnordered(byte idx) { throw XEC.TODO(); }
  public void deleteUnordered(byte idx) { throw XEC.TODO(); }
  public AryUInt8 delete(long idx) {
    System.arraycopy(_es,(int)idx+1,_es,(int)idx,--_len-(int)idx);
    return this;
  }


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
    if( !(o instanceof AryUInt8 ary) ) return false;
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
  @Override public Iterator<UInt8> iterator() { throw XEC.TODO(); }

  // --- Freezable
  @Override public AryUInt8 freeze(boolean inPlace) {
    if( _mut==Mutability.Constant ) return this;
    if( !inPlace ) return construct(Mutability.Constant,this);
    _mut = Mutability.Constant;
    return this;
  }

  // --- Appender
  @Override public AryUInt8 appendTo( UInt8 s ) { return add(s); }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len*5; }

}
