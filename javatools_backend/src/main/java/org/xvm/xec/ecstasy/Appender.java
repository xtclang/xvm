package org.xvm.xec.ecstasy;
import org.xvm.xec.XTC;

public interface Appender<E extends XTC> {
  abstract Appender<E> appendTo(E xtc);
  default Appender<E> ensureCapacity(int count) { return this; }
}
