package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Boolean;
import org.xvm.xec.ecstasy.Enum;
import org.xvm.xec.ecstasy.Freezable;
import org.xvm.xec.ecstasy.Iterable;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.AbstractRange;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xec.ecstasy.text.Char;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xrun.Never;

// ArrayList with a saner syntax and an exposed API for direct use by code-gen.
// Not intended for hand use.
public abstract class Array<E extends XTC> extends XTC implements Iterable<E>, Stringable, Freezable {
  public static final Array GOLD = null;
  public Array(Never n) { _gold=null; _mut=null; } // No-arg-no-work constructor

  public final E _gold;         // Golden instance type
  public Mutability _mut;
  public int _len;

  Array( E gold, Mutability mut, int len ) { _gold=gold; _mut = mut; _len=len; }

  public static Array $new( XTC gold, Mutability mut, Array ary ) {
    if( ary._len==0 ) {
      if( gold == Boolean.GOLD ) return new Aryboolean(mut, Aryboolean.EMPTY);
      if( gold == Char   .GOLD ) return new Arychar   (mut, Arychar   .EMPTY);
      if( gold == Int64  .GOLD ) return new Arylong   (mut, Arylong   .EMPTY);
      if( gold == org.xvm.xec.ecstasy.text.String.GOLD )  return new AryString(mut, AryString.EMPTY);
      throw XEC.TODO();
    }
    assert gold==ary._gold : "Given GOLD: " + gold + " and an ary.GOLD: " + ary._gold;
    
    if( gold == Boolean.GOLD ) return new Aryboolean(mut, (Aryboolean)ary);
    if( gold == Char   .GOLD ) return new Arychar   (mut, (Arychar   )ary);
    if( gold == Int64  .GOLD ) return new Arylong   (mut, (Arylong   )ary);
    if( gold == org.xvm.xec.ecstasy.text.String.GOLD )  return new AryString(mut, (AryString)ary);
    throw XEC.TODO();
  }
  
  /** Empty, as encoded as a size property read */
  public final boolean empty$get() { return _len==0; }
  
  /** Length, as encoded as a size property read */
  public final int size$get() { return _len; }

  public final E Element$get() { return _gold; }
      
  /** Element at */
  abstract public E at( long idx );
  
  /** Add an element, doubling base array as needed */
  abstract public Array<E> add( E e );

  /** Slice */
  abstract public Array<E> slice( AbstractRange r );
  
  /** @return an iterator */
  abstract public Iterator<E> iterator();

  @Override public Mutability mutability$get() { return _mut; }
  @Override public int mutability$getOrd() { return _mut.ordinal(); }

  public Array<E> toArray(Mutability mut, boolean inPlace) {
    if( inPlace && (mut == null || mut == _mut) ) return this;
    if( mut == Mutability.Constant ) return freeze(inPlace);
    if( !inPlace || mut.ordinal() > _mut.ordinal())
      return $new(_gold, mut, this);  // return a copy that has the desired mutability
    _mut = mut;
    return this;
  }

  static final SB SBX = new SB();
  abstract public String toString();

  // --- Freezable
  abstract public Array<E> freeze( boolean inPlace );

  // --- Comparable
  abstract public boolean equals( Object o );

  // --- Hashable
  abstract public int hashCode( );

  // --- Mutability
  public enum Mutability {
    Constant,                   // Deeply immutable
    Persistent,                 // Odd name, but shallow immutable.  Deeper elements can change.
    Fixed,                      // Tuples and arrays are fixed length, but mutable array contents
    Mutable;                    // Classic mutable    
    public static final Mutability[] VALUES = values();
    public static final Enum GOLD = Enum.GOLD; // Dispatch against Ordered class same as Enum class
  }

  // --- text/Stringable
  @Override public long estimateStringLength() { return _len*10L; }

  // --- Comparable
  public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }
  
  public static <E extends XTC> boolean equals$Array(Array<E> gold, Array<E> a0, Array<E> a1) {
    if( a0 == a1 ) return true;
    if( a0._len != a1._len ) return false;
    for( int i=0; i<a0._len; i++ )
      // The element test is based on the gold array element, not either input
      // array elements - which can differ.
      if( !gold._gold.equals(a0.at(i),a1.at(i)) )
        return false;
    return true;
  }
}
