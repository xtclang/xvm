package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;

/**
 * Represent a version number.
 */
public class VerCon extends LitCon {
  private Version _ver;
  public VerCon( FilePart X, Format format  ) {
    super(X, format);
  }

  public Version ver() { return _ver; }
  @Override public void resolve( CPool pool ) {
    super.resolve(pool);
    _ver = new Version(_str._str);
  }
}


