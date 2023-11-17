package org.xvm.xec.ecstasy;
public interface Appenderchar {
  Appenderchar add(char v);
  Appenderchar addAll(String s);
  //Appenderchar addAll(Iterablechar iterable) {
  //  return ensureCapacity(iterable.size).addAll(iterable.iterator());
  //}
  //Appenderchar addAll(Iteratorchar iter) {
  //  @Volatile Appender result = this;
  //  iter.forEach(e -> {result = result.add(e);});
  //  return result;
  //}
  Appenderchar appendTo(String s);
  Appenderchar appendTo(long l);
  default Appenderchar ensureCapacity(int count) { return this; }
}
