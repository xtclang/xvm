package org.xvm.cc_explore.cons;

import java.io.IOException;

/**
  Exploring XEC Constants
 */
public class LiteralCon extends Const {
  final Format _f;
  private transient int _x;     // Index for actual const
  LiteralConst( XEC.XParser X, Const.Format f ) throws IOException {
    _f = f;
    _x = X.index();
  }

  @Override void resolve( CPool pool ) {
    throw XEC.TODO();
  }

}
