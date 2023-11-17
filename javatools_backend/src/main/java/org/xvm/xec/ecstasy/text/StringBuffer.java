package org.xvm.xec.ecstasy.text;

import org.xvm.xec.ecstasy.Appenderchar;
import org.xvm.xec.ecstasy.collections.Arychar;

public class StringBuffer extends Arychar<StringBuffer>
  implements Appenderchar, Stringable
{

  @Override public StringBuffer addAll(String s) { return appendTo(s); }
  @Override public StringBuffer appendTo(String s) {
    for( int i=0; i<s.length(); i++ )
      add(s.charAt(i));
    return this;
  }

  public StringBuffer appendTo(long x) { return appendTo(Long.toString(x)); }

  @Override public String toString() { return new String(_cs,0,_len); }
}
