package org.xvm.xec.ecstasy;
import org.xvm.xec.ecstasy.text.Char;

public interface Appenderchar extends Appender<Char> {
  Appenderchar add(char v);
  Appenderchar addAll(String s);
  Appenderchar appendTo(org.xvm.xec.ecstasy.text.String s);
  Appenderchar appendTo(java.lang.String s);
  Appenderchar appendTo(long l);
  default Appenderchar ensureCapacity(int count) { return this; }
}
