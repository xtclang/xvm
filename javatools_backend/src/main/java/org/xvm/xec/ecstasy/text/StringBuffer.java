package org.xvm.xec.ecstasy.text;

import org.xvm.XEC;
import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.collections.Arychar;

import static org.xvm.xec.ecstasy.collections.Array.Mutability.*;

public class StringBuffer extends Arychar
  implements Appenderchar, Stringable
{

  public StringBuffer( ) {}
  public StringBuffer(long estSize ) { super(Mutable,0, new char[(int)estSize]); }

  public static StringBuffer construct(long estSize) { return new StringBuffer(estSize); }
  public static StringBuffer construct() { return new StringBuffer(8); }

  public StringBuffer add(char v) {
    return (StringBuffer)super.add(v);
  }

  @Override public StringBuffer appendTo( org.xvm.xec.ecstasy.text.String s) {
    for( int i=0; i<s.length(); i++ )
      add(s.charAt(i));
    return this;
  }
  @Override public StringBuffer addAll( java.lang.String s) { return appendTo(s); }
  @Override public StringBuffer appendTo( java.lang.String s) {
    for( int i=0; i<s.length(); i++ )
      add(s.charAt(i));
    return this;
  }
  public StringBuffer append( java.lang.String s) { return appendTo(s); }

  public StringBuffer appendTo(long x) { return appendTo(Long.toString(x)); }

  @Override public java.lang.String toString() { return new java.lang.String(_es,0,_len); }
}
