package org.xvm.xec.ecstasy.text;

import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.collections.Arychar;

public class StringBuffer extends Arychar
  implements Appenderchar, Stringable
{

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

  public StringBuffer appendTo(long x) { return appendTo(Long.toString(x)); }

  @Override public java.lang.String toString() { return new java.lang.String(_es,0,_len); }
}
