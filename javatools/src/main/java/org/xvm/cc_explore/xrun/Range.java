package org.xvm.cc_explore.xrun;

import java.lang.Iterable;
import java.util.Iterator;

/**
     Support XTC range iterator
*/
abstract class Range implements Iterable<Long> {
  final long _lo, _hi;          // Inclusive lo, exclusive hi
  Range( long lo, long hi ) { _lo=lo; _hi=hi; }

  /** @return an iterator */
  @Override public Iterator<Long> iterator() { return new Iter(); }
  private class Iter implements Iterator<Long> {
    long _i=_lo;
    @Override public boolean hasNext() { return _i<_hi; }
    @Override public Long next() { return _i++; }
  }
  
}

