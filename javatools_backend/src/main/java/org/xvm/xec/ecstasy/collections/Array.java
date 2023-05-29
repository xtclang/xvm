package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.Boolean;
import org.xvm.xec.ecstasy.Enum;
import org.xvm.xec.ecstasy.Iterable;
import org.xvm.xec.ecstasy.Iterator;
import org.xvm.xec.ecstasy.Range;
import org.xvm.xec.ecstasy.numbers.Int64;
import org.xvm.xec.ecstasy.text.Char;
import org.xvm.xec.ecstasy.text.Stringable;
import org.xvm.xrun.Never;

import java.util.Arrays;
import java.util.function.LongFunction;

// ArrayList with a saner syntax and an exposed API for direct use by code-gen.
// Not intended for hand use.
public abstract class Array<E extends XTC> extends XTC implements Iterable<E>, Stringable {
  public static final Array GOLD = null;
  public Array(Never n) { _gold=null; _mut=null; } // No-arg-no-work constructor

  public final E _gold;         // Golden instance type
  public final Mutability _mut;
  public int _len;

  Array( E gold, Mutability mut, int len ) { _gold=gold; _mut = mut; _len=len; }

  public static Array $new( XTC gold, Mutability mut, Array ary ) {
    if( ary._len==0 ) {
      if( gold == Int64.GOLD )  return new Arylong(mut, Arylong.EMPTY);
      if( gold == Char .GOLD )  return new Arychar(mut, Arychar.EMPTY);
      if( gold == Boolean.GOLD ) return new Aryboolean(mut, Aryboolean.EMPTY);
      if( gold == org.xvm.xec.ecstasy.text.String.GOLD )  return new AryString(mut, AryString.EMPTY);
      throw XEC.TODO();
    }
    assert gold==ary._gold : "Given GOLD: " + gold + " and an ary.GOLD: " + ary._gold;
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
  abstract public Array<E> at( Range r );
  
  /** @return an iterator */
  abstract public Iterator<E> iterator();

  @Override public Mutability mutability$get() { return _mut; }

  static final SB SBX = new SB();
  abstract public String toString();
  
  abstract public boolean equals( Object o );
  
  abstract public int hashCode( );


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

  // --- Comparable
  public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }
  
  public static boolean equals$Array(Array gold, Array a1, Array a2) {
    throw XEC.TODO();
  }
}
