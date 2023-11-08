package org.xvm.cons;

import org.xvm.*;

/**
 * Represent a version number.
 */
public class VerCon extends LitCon {
  private Version _ver;
  public VerCon( CPool X, Format format  ) { super(X, format); }
  public Version ver() { return _ver; }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _ver = new Version(_str);
  }
}



