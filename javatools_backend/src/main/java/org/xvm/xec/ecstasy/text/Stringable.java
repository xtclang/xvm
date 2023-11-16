package org.xvm.xec.ecstasy.text;
import org.xvm.xec.ecstasy.collections.Arychar;
public interface Stringable {
  default int estimateStringLength() { return 0; }
  default Arychar appendTo(Arychar buf) { return buf.appendTo(toString()); }
}
