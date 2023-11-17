package org.xvm.xec.ecstasy.text;
import org.xvm.xec.ecstasy.Appenderchar;
public interface Stringable {
  default long estimateStringLength() { return 0; }
  Appenderchar appendTo(Appenderchar buf);
}
