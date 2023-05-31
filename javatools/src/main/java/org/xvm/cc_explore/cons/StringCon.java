package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.ArrayList;

/**
  Exploring XEC Constants
 */
public class StringCon extends Const {
  final String _str;
  public StringCon( XEC.XParser X ) throws IOException {
    _str = X.utf8();
  }
  @Override public void resolve( CPool pool ) {}
}
