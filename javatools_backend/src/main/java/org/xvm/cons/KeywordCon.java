package org.xvm.cons;

import org.xvm.*;

/**
  Exploring XEC Constants
 */
public class KeywordCon extends Const implements IdCon {
  private final Format _f;
  public KeywordCon( Format f ) { _f = f;  }
  @Override public String name() { return _f.toString(); }
}
