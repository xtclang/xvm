package org.xvm.cc_explore;

import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class LitConst extends Const {
  final Format _f;
  private transient int _x;     // Index for actual const
  private StringConst _str;     // The actual string constant
  LitConst( XEC.XParser X, Const.Format f ) throws IOException {
    _f = f;
    _x = X.index();
  }

  @Override void resolve( CPool pool ) {
    _str = (StringConst)pool.get(_x);
  }

}
