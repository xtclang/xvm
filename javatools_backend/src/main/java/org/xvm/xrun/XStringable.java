package org.xvm.xrun;
public interface XStringable {
  default int estimateStringLength() { return 0; }
  default Arychar appendTo(Arychar buf) { return buf.appendTo(toString()); }
}
