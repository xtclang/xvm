package org.xvm.cc_explore;

import java.io.IOException;
import java.util.ArrayList;

/**
  Exploring XEC Constants
 */
public class StringConst extends Const {
  final String _str;
  StringConst( XEC.XParser X ) throws IOException {
    _str = X.utf8();
  }
  @Override void resolve( CPool pool ) {}
}
